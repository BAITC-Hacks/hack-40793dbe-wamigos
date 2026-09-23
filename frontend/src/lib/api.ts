import { config } from "./config";
import { ApiError, responseError } from "./errors";
import type {
  AcceptedMeeting,
  ExportFormat,
  Meeting,
  MeetingLink,
  UploadInput,
} from "./types";

export async function uploadMeeting(
  input: UploadInput,
): Promise<AcceptedMeeting> {
  if (config.mock) return (await import("./mock")).uploadMock(input);
  const body = new FormData();
  body.set("file", input.file);
  body.set("source", input.source);
  body.set("title", input.file.name.replace(/\.[^.]+$/, "").slice(0, 200));
  if (input.startedAt) body.set("startedAt", input.startedAt);
  if (input.timeZone) body.set("timeZone", input.timeZone);
  const response = await fetch(`${config.apiBase}/api/v1/meetings`, {
    method: "POST",
    body,
  });
  if (!response.ok) throw await responseError(response);
  if (response.status !== 202) throw new ApiError(502);
  const data: AcceptedMeeting = await response.json();
  if (!data.id || !data.accessToken || !data.createdAt) throw new ApiError(502);
  return data;
}
export async function getMeeting(
  link: MeetingLink,
  signal?: AbortSignal,
): Promise<Meeting> {
  if (config.mock) return (await import("./mock")).getMock(link);
  const response = await fetch(
    `${config.apiBase}/api/v1/meetings/${encodeURIComponent(link.id)}`,
    {
      headers: { Authorization: `Bearer ${link.accessToken}` },
      signal,
      cache: "no-store",
    },
  );
  if (!response.ok) throw await responseError(response);
  return response.json();
}
export async function exportMeeting(
  link: MeetingLink,
  format: ExportFormat,
): Promise<Blob> {
  if (config.mock) throw new ApiError(409, "MOCK_EXPORT_UNAVAILABLE");
  const response = await fetch(
    `${config.apiBase}/api/v1/meetings/${encodeURIComponent(link.id)}/export?format=${format}`,
    { headers: { Authorization: `Bearer ${link.accessToken}` } },
  );
  if (!response.ok) throw await responseError(response);
  const type = response.headers.get("content-type") ?? "";
  const expected =
    format === "pdf"
      ? "application/pdf"
      : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  if (!type.includes(expected) && !type.includes("application/octet-stream"))
    throw new ApiError(502);
  const blob = await response.blob();
  if (!blob.size) throw new ApiError(502);
  return blob;
}
