# Code Rules — digitalplatform-microservice-16

## Layer Direction
```
Controller → Processor → DomainService → Repository
```
- **Controller**: validates transport shape, returns `ResponseEntity<T>`, delegates to Processor
- **Processor**: orchestrates use cases, cross-domain flow. Works ONLY with DTOs via domain service interfaces. Never touches entities or mappers.
- **DomainService**: owns business logic and entity persistence. Interface + Impl in same package. Never calls Processor. Never returns Entity to another layer.
- **Validator**: Pure validation logic only. Never calls repositories or services. Accepts entities/DTOs, returns void or throws. Lookup is the caller's responsibility.
- **Mapper**: Entity ↔ DTO only. No DB calls, no API calls, no business logic. Called ONLY by service impls.
- **Repository**: persistence only.

**Forbidden**: Controller→Repository, Mapper→Service/Processor/Repository, Service→Processor, Service→Service cycles.

## Naming
- Services: `{Domain}Service` / `{Domain}ServiceImpl` — business logic, @Transactional
- Processors: `{Domain}Processor` — orchestrate multiple services
- Controllers: `{Domain}Controller` — REST endpoints
- Mappers: `{Domain}Mapper` — Entity ↔ DTO, pure field copying. One mapper per domain package. Hand-written only, no MapStruct.
- DTOs: `*Request` (incoming), `*Response` (outgoing), `*Dto` (internal transfer). `@Builder` on all. Standalone files — no nested classes.

## Domain Service and DTO Identity
- A domain service exists only for exactly one entity and has the exact name `{Entity}Service` / `{Entity}ServiceImpl`. `Item` therefore uses `ItemService`; a differently named service is not a domain service.
- A `*Dto` is the general DTO for exactly one entity and copies that entity's complete domain shape. Its name is `{Entity}Dto`.
- `*Request` is transport input and `*Response` is transport output. `*ListResponse` wrappers are allowed for collection endpoints. Other composite DTOs (`*Result`, `*Item`, `*Batch`, `*Payload`, `*Document`, and ad-hoc context DTOs) remain forbidden.
- A current caller is not a reason to keep a service or DTO. Keep it only when an active product document owns the behavior; then rename it to the allowed component type or move the behavior into its owning entity service.

## Entities
- Audit: `createdAt`, `updatedAt`. Lombok `@Getter`/`@Setter`, no `@Data`.
- Entities stay inside domain package, never returned from API.
- Use enums for statuses. `FetchType.LAZY` for entity references.
- No primitive types — use wrappers (`Integer`, `Boolean`, `Double`).
- No concrete collection types in declarations — use interfaces (`List`, `Map`).
- No records/classes inside interfaces. No static inner classes in DTOs.
- No anonymous classes (including `new TypeReference<>() {}`).

## Service Rules
- Interface must not expose entities. Return DTO copies only. Accept UUIDs and DTOs only.
- Each Service owns one entity type. Even slight naming differences → separate services.
- ServiceImpl: inject only own entity's repository. For other entities in same domain → call that entity's domain service.
- `getReferenceById()` is the ONLY allowed cross-entity repository access (JPA proxy for relationships).
- **Get-and-validate pattern**: When fetching a domain entity + validating it is used in more than one method, extract a private `getAndValidate(UUID id)` that calls own repository + own domain validator. If different validation is needed for the same entity, create a separate private method with a distinct name and the corresponding validator call.
- **Cross-domain validation**: To validate a non-domain entity, call `otherDomainValidator.validate(entity)` or `otherDomainValidator.validate(entity.getId())`. The entity must be fetched by the caller, not the validator. For cross-domain entities used only for relationship setting, fetch via `findById()` (one query, validates existence immediately) — never `getReferenceById()` + separate exists check.
- Services never set entity fields manually → delegate to Mapper. Composite result DTOs use `@Builder` directly in impl.
- `@Transactional` on mutation methods. `@Transactional(readOnly = true)` allowed on read queries where the caller benefits from Hibernate session consistency.
- Use `@RequiredArgsConstructor`. Never throw `RuntimeException` — use exception hierarchy.
- Never call `findAll()` — always filtered query methods with specific criteria.

## Validation Rules — Spec-Driven Input Checks

When implementing endpoints, always propose validation rules based on the spec's field descriptions. The spec defines what's valid — the code must enforce it.

### Mandatory validation categories (check against spec for every input field):
- **Format**: IIN must be 12 digits, no letters. Phone must match documented format. Email format. Date not in future / not in past.
- **Range**: Port 1-65535. Age 0-150. Amount > 0. Duration > 0. Slot count 1-N.
- **Length**: String max lengths (names, codes, descriptions). IIN exactly 12. Code exact length if spec says so.
- **Enum**: Status values must be from documented set. Type codes must match documented list.
- **Required**: Null/empty checks for every field the spec marks as mandatory.
- **Relationship**: Referenced entity must exist (checked via exists or getReferenceById + existence guard).

### How to apply:
1. Read the spec's field descriptions BEFORE writing the controller/service
2. For each field with a constraint in the spec, propose a validation check
3. If the spec is silent but the field has an obvious real-world constraint (e.g., port number), ask: "Spec doesn't define max port — should I enforce 1-65535?"
4. Never silently add validations the spec doesn't mention — always propose and ask

### Validation placement:
- Field-level constraints (`@NotNull`, `@Size`, `@Pattern`, `@Min`, `@Max`) → DTO or Request class
- Business constraints (enum validation, relationship existence) → Service or Validator
- Never duplicate the same validation in both Controller and Service

## Error Code Rules — Spec-First Error Mapping

### Non-negotiable:
- The HTTP status code returned MUST match what the spec documents for that error scenario
- The error code string in the response body MUST match the spec's documented error code
- Never throw `ValidationException` (400) for a scenario the spec documents as 401, 403, or 409
- Use the correct exception class:
  - `UnauthorizedException` → 401 (auth/token problems)
  - `ForbiddenException` → 403 (access denied to owned resource)
  - `NotFoundException` → 404 (resource missing)
  - `ConflictException` → 409 (state conflict — wrong status, duplicate, already exists)
  - `ValidationException` → 400 (malformed input ONLY — bad format, missing field, out of range)

### Exception class selection checklist (for EVERY error throw):
```
Is the error about missing/expired token or auth? → UnauthorizedException (401)
Is the error about access rights to existing resource? → ForbiddenException (403)
Is the error about resource not existing? → NotFoundException (404)
Is the error about conflicting state (wrong status, duplicate, etc.)? → ConflictException (409)
Is the error about malformed input (bad format, range, required field)? → ValidationException (400)
```

### ErrorCode enum rules:
- Each documented error scenario gets its own `ErrorCode` constant with a clear name matching the spec
- Error message templates use `%s` placeholders for runtime values — never hardcode values in the template
- Group new ErrorCode constants under the correct exception type comment header
- Never reuse `VALIDATION_ERROR` or `CONFLICT` (empty template) for new scenarios — create a named constant

### Post-task spec verification:
After completing a task that touches error handling, run the `spec-error-check` skill. It will:
1. Compare every thrown exception against the spec's documented error codes
2. Flag any mismatch between spec-documented code and actual thrown code
3. Ask if anything is unclear — never guess about spec intent

## DRY — Reuse, Never Rewrite

### The rule
Before writing any logic, search for existing implementations that do the same thing. If found — reuse via a public method call. Never copy-paste logic. Never inline entity access.

### Raw entity access is forbidden
Never access entity fields directly from outside the entity's domain service. If two callers need the same entity operation, extract it into a public method on the owning domain service.

```
BAD — raw entity access repeated in two callers:
  Patient patient = patientRepository.findById(patientId).orElseThrow(...);
  patient.setStatus(ACTIVE);  // caller 1 touches entity directly
  Patient patient = patientRepository.findById(patientId).orElseThrow(...);
  patient.setStatus(ACTIVE);  // caller 2 repeats the same logic

GOOD — extract once, reuse:
  // In PatientService:
  public PatientDto activate(Long patientId) { ... }
  // Callers call patientService.activate(patientId)
```

### Where to extract reusable logic (follows Layer Direction)

| If the reusable logic... | Extract into |
|---|---|
| Maps entity ↔ DTO | Mapper (pure field copying) |
| Validates field format, range, constraints | Validator (never calls repo/service) |
| Fetches or mutates one entity type | That entity's DomainService |
| Orchestrates across multiple domain services | Processor |
| Constructs a response from multiple sources | Service (not mapper — mapper is pure) |

### How to apply
1. When writing logic that touches an entity → check: "Is this the owning domain service?"
2. If no → does the owning service already have a public method for this? If yes → use it. If no → add it there, then call it.
3. When you see the same entity-access pattern in two places → extract immediately, don't wait for a third occurrence.
4. Never write `repository.findById(...)` outside the owning domain service — call `service.getById(...)` or `service.getAndValidate(...)` instead.

### Cross-domain entity access
To get an entity owned by another domain:
- Call `otherDomainService.getReferenceById(id)` for relationship setting (JPA proxy, no DB hit)
- Call `otherDomainService.getById(id)` for validation/read (hits DB, validates existence)
- Never inject another domain's repository directly

## Additional Hackathon Backend Rules

These rules supplement the complete code rules above without replacing them.

- Select backend technology only after reading the released task. Use framework-provided server functionality when it is sufficient; choose Spring Boot when substantial backend logic, concurrency, integrations, domain processing, or persistent workflows justify it.
- Use the smallest architecture that supports the mandatory flows. Do not introduce microservices or distributed infrastructure unless the real task requires them.
- Do not create a backend project before the official task is known.
- Add a database only when required by product behavior.
- Load secrets and API keys only from environment variables. Keep configuration centralized and environment-aware.
- Log useful operational facts concisely without leaking secrets or sensitive data.
- Centralize practical error handling while preserving the specification-first status and error-code rules above.
- Keep external integrations behind small replaceable boundaries so one provider can be changed without rewriting the product.
- Keep AI functionality in the backend when that improves security, orchestration, or reliability.
- Verify the active transaction boundary before claiming that a nested operation can partially commit.
