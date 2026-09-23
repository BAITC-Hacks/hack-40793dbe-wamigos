# HackAlem AI Service

Python service for meeting speech processing and structured fact extraction.
The text pipeline processes RU, KK and mixed transcripts locally: evidence-bound fact
extraction, independent task verification, conservative deadlines, evidence-derived tags,
and a nonempty summary. The speech baseline normalizes media with FFmpeg, transcribes it with
faster-whisper `large-v3`, separates speakers with public SpeechBrain ECAPA embeddings and
local clustering, and builds the same transcript segment contract consumed by text intelligence.

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
export HF_HUB_DISABLE_XET=1
export LD_LIBRARY_PATH="$VIRTUAL_ENV/lib/python3.12/site-packages/nvidia/cublas/lib:$VIRTUAL_ENV/lib/python3.12/site-packages/nvidia/cudnn/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
LOCAL_LLM_MODEL=qwen3:4b-instruct uvicorn app.main:app --host 127.0.0.1 --port 8000 --workers 1
```

Run the commands from the `ai-service` directory.
Alternatively create `.env` using the variables shown in `.env.example`.

The default `speechbrain/spkrec-ecapa-voxceleb` speaker model is public and downloads without
`HF_TOKEN`. `DIARIZATION_MODEL_PATH` can point to a compatible local SpeechBrain model.
Speech models load only when audio analysis is invoked. The default releases them after each
run so an 8 GB GPU can be reused by the local LLM; this trades throughput for predictable
memory usage. Media and transcripts are not sent to a cloud provider.

For the complete Docker setup, copy the root `.env.example` to `.env` and run
`docker compose up --build --wait` from the repository root. Compose starts PostgreSQL, Ollama,
pulls the configured Qwen model, starts this service with one Uvicorn worker, then starts Java
and the frontend. `HF_HUB_DISABLE_XET=1` is set in the container, and downloaded Hugging Face
models are preserved in a named volume.

## Endpoints

- `GET /health`
- `POST /dev/analyze-transcript`
- `POST /internal/v1/analyze`

`/dev/analyze-transcript` is development-only. Set `ENABLE_DEV_ENDPOINTS=false` to remove
the route and its OpenAPI entry at startup. It accepts prepared segments and optional
`context.startedAt` / `context.timeZone`.

`/internal/v1/analyze` is the synchronous Java integration boundary. Send multipart fields
`file`, optional `startedAt`, and optional `timeZone`. Python creates no jobs or polling state:
the uploaded media is stored in a temporary directory, passed through the audio and text
pipelines, returned as `MeetingAnalysisResult`, and deleted before the request completes.

```bash
curl -sS http://127.0.0.1:8000/dev/analyze-transcript \
  -H 'Content-Type: application/json' \
  -d '{"context":{"startedAt":"2026-09-23T14:00:00+05:00","timeZone":"Asia/Almaty"},"segments":[{"id":"seg-1","speakerId":"SPEAKER_00","startMs":0,"endMs":5000,"text":"Поставщик задержал оборудование. Пусть Ерлан подготовит претензию до пятницы."}]}'
```

The camelCase response keeps `durationMs`, `summary`, `speakers`, `segments`, `tasks`,
`problems`, and `diagnostics`. Every LLM candidate must include exact `evidenceQuotes`.
`EvidenceBinder` finds each quote in exactly one transcript segment, corrects a wrong source
ID, and excludes missing or ambiguous evidence. Published tasks and problems have nonempty
`sourceSegmentIds` pointing to original segments. Duplicate fact IDs are rejected. Speaker
IDs cannot be used as person names. Exact repeated action text for the
same assignee triggers repair so confirmations and deadline corrections can be consolidated.
Distinct work with the same assignee must state its different scope in the task text.
Source text, speaker IDs and timestamps are preserved; `TASK` / `PROBLEM` tags
are rebuilt from extracted references, including both tags on the same segment.

Textual speaker resolution accepts explicit name labels and self-introductions. Conflicting
or missing identity stays null; an addressee is never automatically the speaker. Conversational
identity inference from diarized audio remains part of the speech stage. No participant-directory
input was added. Assigner/reporter names are recomputed from resolved speakers in bound evidence.
Problems use exact source excerpts so numeric meaning cannot change through paraphrasing.

The verifier receives all candidates and the complete transcript in one batch. Only
`CONFIRMED` and evidence-validated `CORRECTED` tasks are published. `REJECTED` and
`REVIEW_REQUIRED` candidates are counted in diagnostics, excluded from TASK tags, and never
sent to summary generation. Missing/duplicate verdicts or ungrounded corrections fail closed
after one repair. Verification is model-based, not a mathematical guarantee of factual accuracy.

Summary generation runs after verification and deadline normalization. The LLM selects up to
five task IDs and three problem IDs from the accepted facts; Python renders their original
texts, assignees and raw deadlines into a string. It cannot add new facts through free-form
summary generation. A no-facts meeting receives an explicit nonempty no-facts summary.

## Deadline policy

`startedAt` must have an offset; `timeZone`, when present, must be a valid IANA zone.
The zone determines the local meeting date; otherwise the supplied offset is used.
Upload time, today's date and the server timezone are never substituted.

- Explicit full calendar dates work without meeting context.
- Dates without a year use the meeting's year only; no guessed next-year rollover.
- `до пятницы / к среде / жұмаға дейін`: nearest named weekday on or after the meeting date.
- `завтра / ертеңге дейін`, `через две недели / за две недели / екі апта ішінде`:
  calendar day/week offsets from that date.
- `до конца недели / апта соңына дейін`, `на этой неделе`, `келесі аптада` and
  event-based wording such as `после совещания` stay in `deadlineRaw` with a null date.
- Event-based, unsupported, impossible or context-dependent ambiguous deadlines remain raw/null.
  Missing deadlines have both fields null. Workday/holiday calendars are not inferred.

## Pipeline and failures

The HTTP route calls `MeetingPipeline.analyze_transcript(segments, context)`.
The pipeline orchestrates speaker resolution, `MeetingFactsExtractor`, `EvidenceBinder`,
deterministic speaker attribution, `DeadlineRawValidator`, `TaskVerifier`,
`DeadlineNormalizer`, `SegmentTagger` and `SummaryGenerator`.
`StructuredLlm` centralizes schema parsing, semantic validation and one repair per stage
above the unchanged `LlmProvider` interface.
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
| 502 | `LLM_INVALID_FACTS` | Extraction, verifier or summary selection still invalid after one repair. |
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
Blocking FFmpeg, faster-whisper and ECAPA/PyTorch inference runs through one controlled
`asyncio.to_thread` call and a concurrency-one lock; declaring inference `async` alone would
not make it nonblocking.

`AudioMeetingPipeline.analyze_file` connects speech segments to the text pipeline. The
production `POST /internal/v1/analyze` endpoint is the synchronous Java integration boundary;
Java owns upload storage, job state and result persistence.

## Local LLM configuration

```dotenv
LOCAL_LLM_BASE_URL=http://localhost:11434/v1
LOCAL_LLM_MODEL=qwen3:4b-instruct
LOCAL_LLM_TIMEOUT_SECONDS=180
LOCAL_LLM_HEALTH_TIMEOUT_SECONDS=3
LOCAL_LLM_MAX_TOKENS=4096
LOCAL_LLM_MAX_INPUT_CHARS=20000
ASR_MODEL=large-v3
ASR_DEVICE=cuda
ASR_COMPUTE_TYPE=float16
DIARIZATION_MODEL=speechbrain/spkrec-ecapa-voxceleb
DIARIZATION_DEVICE=cuda
DIARIZATION_SIMILARITY_THRESHOLD=0.55
DIARIZATION_MAX_SPEAKERS=8
SPEECH_RELEASE_MODELS_AFTER_RUN=true
```

The request timeout bounds each inference attempt, including network access. Each of the three
LLM stages may use one repair (at most six inference calls in total); transport errors are
not retried. Diagnostics expose per-stage repair counts and elapsed times.
`MAX_INPUT_CHARS` limits the serialized meeting payload and is not a
token count. Size the runtime context for the input, schema, prompt, and output together.
Changing local runtime/model requires support for JSON-schema responses and `/v1/models`.

## Reproducible checks

No extra test dependencies are required:

```bash
python -m unittest discover -s tests -v
python -m evaluation.run_protocols
python -m evaluation.run_edge_cases
python -m evaluation.run_protocols --model qwen2.5:7b-instruct
```

The protocol evaluation calls the REAL configured local model and exits nonzero for missing
expected task groups, incorrect dates/assigners/reporters, malformed evidence or provider errors.
It covers the complete dialogue of Protocols 1 and 2, not snippets. The reference answer tables
and summaries are never included in the model input. Source speaker headings are retained as
text labels; timestamps and the September 23, 2026 context are explicitly synthetic evaluation
anchors, not measured audio times or asserted meeting dates. Fixtures link to the source files.
Phrase-based acceptance checks complement manual reading; they are not general precision/F1
measurements or proof of unseen-data accuracy. Do not call a model fully validated based only
on these two documents.
