export const config = {
  apiBase: (process.env.NEXT_PUBLIC_API_BASE_URL ?? "").replace(/\/$/, ""),
  mock: process.env.NEXT_PUBLIC_MOCK_API === "true",
  pollMs: 3_000,
  maxHistory: 5,
  maxBytes: 500 * 1024 * 1024,
  maxRecordingMs: 60 * 60 * 1000,
  storageKey: "protokol.meetings.v1",
  mockStorageKey: "protokol.demo-links.v1",
};
