"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import type { ReactNode } from "react";
import { getMeeting } from "@/lib/api";
import { config } from "@/lib/config";
import { ApiError, errorMessage } from "@/lib/errors";
import { isExpired, readLinks, saveLinks } from "@/lib/storage";
import type { Meeting, MeetingLink } from "@/lib/types";

interface MeetingsContext {
  links: MeetingLink[];
  meetings: Record<string, Meeting>;
  errors: Record<string, string>;
  loaded: boolean;
  storageAvailable: boolean;
  missing: Set<string>;
  add: (link: MeetingLink) => void;
  refresh: (id: string) => Promise<void>;
}
const Context = createContext<MeetingsContext | null>(null);

export function MeetingsProvider({ children }: { children: ReactNode }) {
  const [links, setLinks] = useState<MeetingLink[]>([]);
  const [meetings, setMeetings] = useState<Record<string, Meeting>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [missing, setMissing] = useState(new Set<string>());
  const [loaded, setLoaded] = useState(false);
  const [storageAvailable, setStorageAvailable] = useState(true);
  const linksRef = useRef<MeetingLink[]>([]);
  const requests = useRef(
    new Map<string, { promise: Promise<void>; controller: AbortController }>(),
  );
  const controllers = useRef(new Set<AbortController>());
  const mounted = useRef(false);

  const store = useCallback((next: MeetingLink[]) => {
    linksRef.current = next;
    setLinks(next);
    setStorageAvailable(saveLinks(next));
  }, []);

  const refresh = useCallback(
    (id: string): Promise<void> => {
      const active = requests.current.get(id);
      if (active) return active.promise;
      const link = linksRef.current.find((item) => item.id === id);
      if (!link) return Promise.resolve();
      const controller = new AbortController();
      controllers.current.add(controller);
      const promise = (async () => {
        try {
          const meeting = await getMeeting(link, controller.signal);
          if (!mounted.current || controller.signal.aborted) return;
          if (isExpired(meeting)) throw new ApiError(404);
          if (!linksRef.current.some((item) => item.id === id)) return;
          setMeetings((previous) => ({ ...previous, [id]: meeting }));
          setErrors((previous) => {
            const next = { ...previous };
            delete next[id];
            return next;
          });
          store(
            linksRef.current.map((item) =>
              item.id === id
                ? {
                    ...item,
                    title: meeting.title,
                    expiresAt: meeting.expiresAt,
                  }
                : item,
            ),
          );
        } catch (error) {
          if (!mounted.current || controller.signal.aborted) return;
          if (error instanceof ApiError && error.status === 404) {
            setMissing((previous) => new Set(previous).add(id));
            store(linksRef.current.filter((item) => item.id !== id));
          } else
            setErrors((previous) => ({
              ...previous,
              [id]: errorMessage(error),
            }));
        } finally {
          controllers.current.delete(controller);
          if (requests.current.get(id)?.controller === controller)
            requests.current.delete(id);
        }
      })();
      requests.current.set(id, { promise, controller });
      return promise;
    },
    [store],
  );

  useEffect(() => {
    mounted.current = true;
    const restored = readLinks();
    linksRef.current = restored.links;
    setLinks(restored.links);
    setStorageAvailable(restored.available);
    setLoaded(true);
    void Promise.all(restored.links.map((link) => refresh(link.id)));
    return () => {
      mounted.current = false;
      controllers.current.forEach((controller) => controller.abort());
      requests.current.clear();
    };
  }, [refresh]);

  useEffect(() => {
    const deadlines = links
      .filter((link) => link.expiresAt !== null)
      .map((link) => Date.parse(link.expiresAt!));
    if (!deadlines.length) return;
    const timer = setTimeout(
      () => {
        const expired = linksRef.current.filter(isExpired);
        if (!expired.length) return;
        const ids = new Set(expired.map((link) => link.id));
        setMissing((previous) => new Set([...previous, ...ids]));
        setMeetings((previous) =>
          Object.fromEntries(
            Object.entries(previous).filter(([id]) => !ids.has(id)),
          ),
        );
        store(linksRef.current.filter((link) => !ids.has(link.id)));
      },
      Math.min(Math.max(0, Math.min(...deadlines) - Date.now()), 2_147_483_647),
    );
    return () => clearTimeout(timer);
  }, [links, store]);

  const add = useCallback(
    (link: MeetingLink) => {
      store(
        [link, ...linksRef.current.filter((item) => item.id !== link.id)].slice(
          0,
          config.maxHistory,
        ),
      );
    },
    [store],
  );

  return (
    <Context.Provider
      value={{
        links,
        meetings,
        errors,
        missing,
        loaded,
        storageAvailable,
        add,
        refresh,
      }}
    >
      {children}
    </Context.Provider>
  );
}

export function useMeetings() {
  const context = useContext(Context);
  if (!context) throw new Error("MeetingsProvider is required");
  return context;
}

export function useMeetingPolling(ids: string[]) {
  const { refresh, meetings, errors } = useMeetings();
  const states = useRef({ meetings, errors });
  states.current = { meetings, errors };
  const key = ids.join(",");
  useEffect(() => {
    let stopped = false;
    let timer: ReturnType<typeof setTimeout>;
    const poll = async () => {
      const active = key
        .split(",")
        .filter(Boolean)
        .filter((id) => {
          const meeting = states.current.meetings[id];
          return (
            !states.current.errors[id] &&
            (!meeting ||
              meeting.status === "QUEUED" ||
              meeting.status === "PROCESSING")
          );
        });
      await Promise.all(active.map(refresh));
      if (!stopped && active.length) timer = setTimeout(poll, config.pollMs);
    };
    void poll();
    return () => {
      stopped = true;
      clearTimeout(timer);
    };
  }, [key, refresh, Object.keys(errors).join(",")]);
}
