# Changelog
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
