export const config = {
  apiBase: (process.env.NEXT_PUBLIC_API_BASE_URL ?? "").replace(/\/$/, ""),
  pollMs: 3_000,
  maxHistory: 5,
  maxBytes: 500 * 1024 * 1024,
  maxRecordingMs: 60 * 60 * 1000,
  storageKey: "protokol.meetings.v1",
};
