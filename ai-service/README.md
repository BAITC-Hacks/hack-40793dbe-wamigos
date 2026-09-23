# HackAlem AI Service

Python service for meeting speech processing and structured fact extraction.
Stage 2 extracts tasks and problems from RU, KK and mixed transcripts using a local LLM.
The model produces JSON constrained by a Pydantic-generated schema. The service validates
the JSON and its source references before returning it to Java.

## Start the local model

Install [Ollama](https://docs.ollama.com/linux), then start its local-only server:

```bash
OLLAMA_NO_CLOUD=1 OLLAMA_HOST=127.0.0.1:11434 OLLAMA_NUM_PARALLEL=1 OLLAMA_CONTEXT_LENGTH=16384 ollama serve
```

In another terminal:

```bash
ollama pull qwen3:4b-instruct
```

The initial development model is [Qwen3 4B Instruct](https://ollama.com/library/qwen3:4b-instruct).
It is a baseline, not a guarantee of Kazakh extraction accuracy. Model weights are downloaded
once; meeting contents are processed locally. Ollama's cloud features are disabled with
`OLLAMA_NO_CLOUD=1` on the model server. No cloud fallback exists in this service.

## Local run

Use Python 3.11 or 3.12.

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
LOCAL_LLM_MODEL=qwen3:4b-instruct uvicorn app.main:app --host 127.0.0.1 --port 8000 --workers 1
```

Run the commands from the `ai-service` directory.
Alternatively create `.env` using the variables shown in `.env.example`.

## Endpoints

- `GET /health`
- `POST /dev/analyze-transcript`

`/dev/analyze-transcript` is development-only. Set `ENABLE_DEV_ENDPOINTS=false` to remove
the route and its OpenAPI entry at startup. It accepts prepared segments and optional
`context.startedAt` / `context.timeZone`.

```bash
curl -sS http://127.0.0.1:8000/dev/analyze-transcript \
  -H 'Content-Type: application/json' \
  -d '{"context":{"startedAt":"2026-09-23T14:00:00+05:00","timeZone":"Asia/Almaty"},"segments":[{"id":"seg-1","speakerId":"SPEAKER_00","startMs":0,"endMs":5000,"text":"Поставщик задержал оборудование. Пусть Ерлан подготовит претензию до пятницы."}]}'
```

The camelCase response keeps `durationMs`, `summary`, `speakers`, `segments`, `tasks`,
`problems`, and `diagnostics`. Every task and problem has nonempty `sourceSegmentIds`
pointing to original segments. Duplicate fact IDs and unknown source references are
rejected. Speaker IDs cannot be used as person names. Exact repeated action text for the
same assignee triggers repair so confirmations and deadline corrections can be consolidated.
Distinct work with the same assignee must state its different scope in the task text.
Source text, speaker IDs and timestamps are preserved; `TASK` / `PROBLEM` tags
are rebuilt from extracted references, including both tags on the same segment.

This stage does not build a resolved speaker directory, normalize dates or generate summaries.
`speakers[].name`, `tasks[].assignerName`, `problems[].reportedBy` and `tasks[].deadlineDate`
remain null; `summary` remains empty. Internal extraction schemas enforce null for the
speaker-dependent fields until verified speaker attribution is implemented. The public
contract keeps those fields available for that later stage. `assigneeName` is extracted
for explicitly named responsible people or departments; `deadlineRaw` preserves the stated
deadline. Context is passed to extraction without
guessing the meeting date or the server's timezone. Extracted facts still require the
later independent verification stage; diagnostics report this explicitly.

## Pipeline and failures

The HTTP route calls `MeetingPipeline.analyze_transcript(segments, context)`.
The pipeline depends on `MeetingFactsExtractor`, which uses the `LlmProvider` protocol.
`LocalLlmProvider` is the sole implementation and uses an asynchronous HTTPX client with
`POST /v1/chat/completions` and `response_format.type=json_schema`. The configured base
URL must end in `/v1`; it is the only inference destination. Redirects and environment
proxies are disabled. The client is shared for the application lifespan and closed on exit.

Malformed JSON, invalid dates from the model, duplicate fact IDs or invalid references
trigger at most one regeneration with validation feedback. The original transcript remains
the source of truth. Incomplete/refused responses and transport failures return an error
immediately. Invalid output never becomes a successful empty result. Transcripts exceeding
the configured character limit are rejected, not truncated; long-meeting chunking is deferred.

| HTTP | Code | Meaning |
| --- | --- | --- |
| 413 | `TRANSCRIPT_TOO_LARGE` | Serialized transcript/context exceeds the input budget. |
| 422 | FastAPI validation error | Invalid input segments, IDs, timestamps or context. |
| 502 | `LLM_REQUEST_REJECTED` | Runtime rejected the inference request or schema. |
| 502 | `LLM_INVALID_RESPONSE` | Invalid, refused or truncated completion envelope. |
| 502 | `LLM_INVALID_FACTS` | Model facts still invalid after one repair attempt. |
| 503 | `LLM_NOT_CONFIGURED` | No local model is configured. |
| 503 | `LLM_MODEL_NOT_FOUND` | Model or local endpoint not found. |
| 503 | `LLM_UNAVAILABLE` | Runtime unreachable, overloaded or failing. |
| 503 | `ANALYSIS_BUSY` | One analysis already running; `Retry-After: 5`. |
| 504 | `LLM_TIMEOUT` | Local inference exceeded its time limit. |

Service errors have the shape `{"code":"LLM_UNAVAILABLE","message":"..."}` and do not
expose provider response bodies. `GET /health` checks that `/v1/models` lists the configured
model: `200` when available, `503` for unavailable runtime, missing model or configuration.
This checks model availability, not extraction quality or model warmup.

Use one Uvicorn worker. At most one analysis runs per process; additional calls fail fast
instead of creating a job queue. Job lifecycle, scheduling and retries belong to Java.
Future blocking Whisper/pyannote/PyTorch inference must run in a controlled worker/thread
outside the event loop. Declaring an inference function `async` does not make it nonblocking.

The production integration endpoint will be `POST /internal/v1/analyze`. Audio ingestion,
speech processing, independent verification, date normalization and summaries are later stages.

## Local LLM configuration

```dotenv
LOCAL_LLM_BASE_URL=http://localhost:11434/v1
LOCAL_LLM_MODEL=qwen3:4b-instruct
LOCAL_LLM_TIMEOUT_SECONDS=180
LOCAL_LLM_HEALTH_TIMEOUT_SECONDS=3
LOCAL_LLM_MAX_TOKENS=4096
LOCAL_LLM_MAX_INPUT_CHARS=20000
```

The request timeout bounds each inference attempt, including network access; a repair may
use a second attempt. `MAX_INPUT_CHARS` limits the serialized meeting payload and is not a
token count. Size the runtime context for the input, schema, prompt, and output together.
Changing local runtime/model requires support for JSON-schema responses and `/v1/models`.
