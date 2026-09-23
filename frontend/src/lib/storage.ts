import { config } from "./config";
import type { MeetingLink } from "./types";

const key = config.mock ? config.mockStorageKey : config.storageKey;
export function isExpired(link: Pick<MeetingLink, "expiresAt">): boolean {
  return link.expiresAt !== null && Date.parse(link.expiresAt) <= Date.now();
}
export function readLinks(): { links: MeetingLink[]; available: boolean } {
  try {
    const data: unknown = JSON.parse(localStorage.getItem(key) ?? "[]");
    const links = Array.isArray(data)
      ? data
          .filter(
            (item): item is MeetingLink =>
              item &&
              typeof item.id === "string" &&
              typeof item.accessToken === "string" &&
              typeof item.title === "string" &&
              typeof item.createdAt === "string" &&
              Number.isFinite(Date.parse(item.createdAt)) &&
              (item.expiresAt === null ||
                (typeof item.expiresAt === "string" &&
                  Number.isFinite(Date.parse(item.expiresAt)))),
          )
          .filter((item) => !isExpired(item))
          .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
      : [];
    return {
      links: Array.from(
        new Map(links.map((item) => [item.id, item])).values(),
      ).slice(0, config.maxHistory),
      available: true,
    };
  } catch {
    return { links: [], available: false };
  }
}
export function saveLinks(links: MeetingLink[]): boolean {
  try {
    localStorage.setItem(
      key,
      JSON.stringify(
        links.map(({ id, accessToken, title, createdAt, expiresAt }) => ({
          id,
          accessToken,
          title,
          createdAt,
          expiresAt,
        })),
      ),
    );
    return true;
  } catch {
    return false;
  }
}
