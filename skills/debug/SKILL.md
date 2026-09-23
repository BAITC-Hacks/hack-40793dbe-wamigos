---
name: debug
description: Use when a concrete failure, error, regression, broken flow, or unexpected behavior may involve code, data, migrations, containers, configuration, or dependent services and needs fast root-cause recovery.
---

# Debug

Restore the complete working scenario as quickly as possible. Treat code as only one part of the running system.

## Workflow

1. Read the failure, expected behavior, and available error evidence.
2. Reproduce only when doing so will materially improve diagnosis.
3. Inspect the smallest relevant execution path across application code, database state, migrations, configuration, containers, and dependent services.
4. Rank plausible causes by probability and impact, then verify them in that order.
5. Trace the failing value or state from its origin through the operation that fails.
6. Choose the fastest safe recovery strategy for the value of the current local state.
7. Fix the root cause without rewriting unrelated working code.
8. Restart the affected stack when runtime state may be stale.
9. Verify the real end-to-end scenario from a fresh application start rather than checking code alone.
10. Stop when the issue is resolved.

## Inspect the Whole Runtime

Do not assume that a correct code path means the product works. Check the parts that can hold stale or incompatible state:

- database schema, rows, relationships, constraints, sequences, and migration history;
- migration tools and seed data;
- Docker containers, images, networks, volumes, health checks, ports, and logs;
- environment variables and active configuration;
- caches, queues, object storage, files, and other local dependencies;
- versions and startup order of connected services.

Use direct SQL and service logs when they can reveal the broken state faster than tracing more source code.

## Choose Reset or Repair

First classify the current state:

- **Disposable local state**: the scenario is quick to recreate and no valuable manual progress will be lost.
- **Expensive local state**: reproducing the scenario requires a long manual flow or contains test data that is difficult to rebuild accurately.
- **Shared, production, or uncertain state**: destructive recovery is not authorized by this skill.

For disposable local state, prefer a clean reset when it is faster and more reliable than repairing unknown drift:

1. Resolve the exact local database, container, volume, or cache target.
2. Confirm that it is non-production, not shared, and recoverable from migrations or setup scripts.
3. Preserve only the evidence needed to understand the failure.
4. Recreate the affected state, apply migrations from the beginning, restart dependencies, and run the scenario again.

For expensive local state, preserve the working scenario and repair only the inconsistent data:

1. Inspect the active schema, migration history, foreign-key relationships, and affected rows.
2. Determine which old and new structures represent the same business state.
3. Use narrowly scoped SQL to copy or transform required data into the active structure.
4. Verify counts, identifiers, relationships, and application behavior before removing obsolete rows.
5. Clean obsolete data or local migration history only when the inconsistency is proven and the exact local target is known.
6. Prefer a database transaction or a focused export when it adds quick recovery without delaying the fix.

When an environment reset is the fastest safe option, it is acceptable to rebuild local containers, recreate local volumes, clear local caches or queues, rerun migrations and seeds, and restart the complete stack. When valuable state would be lost, targeted SQL repair is preferable even if resetting would be simpler.

Never apply destructive database, volume, queue, cache, or migration-history operations to production, shared, remote, or unidentified resources. If the environment cannot be proven local and disposable, stop and ask the user.

Do not refactor unrelated systems, scan the entire repository when evidence localizes the failure, create automated test suites unless explicitly requested, preserve disposable hackathon state at the cost of a much slower recovery, or produce a long report.

Report briefly: the initial bad value or state, where and when it should have been set, which earlier check or operation failed, why that stopped the flow, whether code or runtime state was repaired or reset, and how the complete scenario was verified.
