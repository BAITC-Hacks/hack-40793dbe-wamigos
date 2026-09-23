# ПРОТОКОЛ — frontend

Next.js 16, React 19, TypeScript. Node.js 22 recommended.

## Run

```sh
cd frontend
npm ci
```

Copy `.env.example` to `.env.local`, configure the Java API URL, then run:

```sh
npm run dev
```

Open http://localhost:3000. Production: `npm run build`, then `npm start`.
Type checking: `npm run typecheck`.

## Java API

`NEXT_PUBLIC_API_BASE_URL` is the Java origin, without `/api/v1`.
An empty value uses the frontend origin and requires an upstream reverse proxy.
Java must allow the frontend origin via CORS, including the Authorization header.
Only the Java `/api/v1/meetings` upload, status and export endpoints are called.
The source of the transport contract is the task's supplied Java API description.
The linked Google Docs could not be accessed. Python is never called by the browser.

Tokens are stored alongside the last five meeting links in localStorage. Audio and
transcripts are not persisted. Clearing browser data loses these links. Use HTTPS
outside localhost for microphone access. Server-side file and duration checks are final.

## Explicit demo mode

Set `NEXT_PUBLIC_MOCK_API=true` and restart the development server, or rebuild production.
The page displays a demo banner. In an empty list, use “Открыть демонстрационные записи”
to see five examples with different statuses. Uploaded demo files transition through
queue and processing to an example result. Demo mode does not analyze audio or generate
PDF/DOCX; export explicitly reports that Java is required. Demo history is separate from
real history. A real API error never enables demo mode.

## Integration checks requiring Java

Actual audio acceptance, queue progress, real results, expiry and PDF/DOCX content require
a running Java backend. Verify the backend's exact problem codes during integration;
codes are aligned with `backend/.../error/ErrorCode.java`; unknown codes fall back to a safe HTTP-status message. `FAILED` is read from the JSON body,
including when HTTP status is 200. Lost POST responses require a manual retry because
the server may already have accepted the file.

## UI and licenses

The Russian UI follows the three supplied references. “RU / KZ” describes supported speech
languages; it is not a nonfunctional language button. Icons and geometric artwork are
local SVGs. No fonts, analytics or assets are requested from external CDNs at runtime.
No third-party UI component source was copied. Next.js, React and TypeScript retain their
upstream license files in their installed npm packages; dependencies are locked in
`package-lock.json`.
