# Changelog
## 2026-09-23 17:24

Changed:
- Made CPU execution the default for the complete Docker Compose stack and added an optional NVIDIA GPU override.
- Simplified the jury README to one cross-platform startup command, prerequisites, and a short verification flow.

Reason:
- Let reviewers start and check local audio processing without a GPU or separate Ollama installation.

## 2026-09-23 13:42

Changed:
- Initialized the project development rules, frontend and backend guidance, Git workflow, and reusable debugging and review skills.
- Set `develop` as the shared integration branch.

Reason:
- Give the team one consistent implementation, integration, debugging, and code-quality workflow from the official project start.

## 2026-09-23 13:53

Changed:
- Added a shared `.gitignore` for frontend, Java backend, Java AI service, local secrets, IDE metadata, logs, caches, and generated artifacts.

Reason:
- Keep the repository clean across all planned services without excluding dependency lock files or safe environment examples.

## 2026-09-23 14:24

Changed:
- Added the initial FastAPI AI Service with environment-based configuration and strict Pydantic contracts.
- Added `GET /health` and the first `POST /api/v1/analyze-transcript` transport slice for transcript validation and normalization.
- Added Python virtual-environment, bytecode, and tool-cache exclusions.

Reason:
- Establish the stable API and domain contract needed before adding LLM-backed extraction, verification, deadline normalization, and summaries.

## 2026-09-23 14:42

Changed:
- Aligned the AI Service result contract with the Java integration shape using camelCase JSON fields for speakers, segments, tasks, problems, and diagnostics.
- Replaced meeting date and cloud-provider fields with meeting context, a development-endpoint toggle, and local LLM configuration.
- Moved transcript analysis behind the asynchronous `MeetingPipeline` boundary and exposed it through `POST /dev/analyze-transcript`.
- Added result validation that rejects task or problem references to unknown segment IDs.

Reason:
- Prepare the text-only development flow for local LLM extraction while keeping the future production endpoint and Java-owned lifecycle separate.

## 2026-09-23 15:12

Changed:
- Added the LlmProvider protocol, a local-only asynchronous HTTP provider, and MeetingFactsExtractor with a versioned RU/KK/mixed-language prompt and Pydantic JSON-schema output.
- Connected real task/problem extraction to the development endpoint with source validation, derived segment tags, one bounded repair attempt, explicit provider errors, and model-aware health checks.
- Added one-analysis-at-a-time admission, HTTP client lifespan management, inference timeouts, and an input budget.
- Constrained internal extraction schemas to leave unverified assigner/reporter identities and normalized deadlines null while preserving the public Java contract.
- Documented Ollama/Qwen3 4B Instruct startup, configuration, error codes, and remaining verification, speaker-resolution, date-normalization and summary stages.

Reason:
- Make the text analysis slice work with a real local model before speech integration, preserve traceable evidence, and avoid claiming verified identity or dates prematurely.

## 2026-09-23 15:15

Changed:
- Added the complete Java 21/Spring Boot meeting-processing backend in `backend/`: PostgreSQL/Flyway persistence, private storage, protected job access, DB-backed worker lifecycle, deterministic mock analysis, PDF/DOCX export, cleanup/timeout handling, OpenAPI, integration tests, Docker configuration, and run documentation.
- Added row locking for timeout transitions and compensation coverage for files saved before a failed database job creation.

Reason:
- Implement the released HackAlem backend specification as a reliable end-to-end Java-only MVP that is ready for the later real Python adapter integration.

## 2026-09-23 15:25

Changed:
- Added the frontend package, Java API DTOs, upload and export transport, browser history, polling, microphone lifecycle, and explicit demo transport.
- Recorded the frontend design based on the three approved screenshots.

Reason:
- Establish the frontend data flow before connecting the screens; this checkpoint does not yet contain the complete application.

## 2026-09-23 15:32

Changed:
- Added home, recent-meetings and protocol routes, screenshot-based responsive styles, recording and upload dialogs, status rendering and export controls.

Reason:
- Connect the frontend data flow to the complete meeting workflow for browser verification.

## 2026-09-23 15:38

Changed:
- Matched frontend error messages to the merged Java ErrorCode contract and corrected recording button contrast.
- Added expiry cleanup for open pages, guarded request cleanup and microphone-disconnection handling.
- Formatted frontend sources and disabled generated Next.js agent files and development indicators.

Reason:
- Complete browser review fixes and prepare integration with the merged backend; production build and TypeScript checks pass.

## 2026-09-23 15:46

Changed:
- Removed all frontend demo data, mock transport, mock configuration, seed actions and demo styles at the team's request.
- Updated frontend documentation and project rules to require Java API data exclusively.

Reason:
- Integrate directly with the running Java backend while its separate AI adapter remains under development.

## 2026-09-23 16:01

Changed:
- Added a root Docker Compose launch for frontend, Java backend, and PostgreSQL with health checks and persistent storage.
- Added standalone frontend and multi-stage Java image builds, environment examples, and build-context exclusions.
- Reworked the root README around the product problem, technologies, one-command jury startup, verification steps, and explicit remaining backend AI mock limitations.

Reason:
- Let judges independently launch and verify the current application without installing language runtimes or mistaking demonstration AI output for real speech recognition.

## 2026-09-23 16:31

Changed:
- Replaced the Java runtime AI mock with a streaming multipart HTTP adapter for the Python `POST /internal/v1/analyze` contract, typed timeouts, transport DTO mapping, response validation, and Actuator health reporting.
- Added stable media and infrastructure error mapping, preserved the existing asynchronous job lifecycle and public API, and allowed contract-valid zero-duration results in persistence.
- Added MockWebServer and embedded-PostgreSQL coverage for success, optional metadata, media errors, HTTP failures, disconnects, timeouts, malformed JSON, invalid references, persistence, and export.
- Updated backend and root runtime configuration and documentation for the real Python dependency while documenting the remaining Python audio-endpoint work.

Reason:
- Complete the Java side of the Python AI integration without buffering large media files or retaining a runtime fallback that could hide unavailable AI processing.

## 2026-09-23 17:50

Changed:
- Added five application screenshots to the root README: home, queue, processing, completed protocol and detailed tasks/problems.

Reason:
- Let judges preview the meeting workflow directly in the repository.
