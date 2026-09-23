"use client";

import Link from "next/link";
import { addedDate, duration } from "@/lib/format";
import type { MeetingStatus } from "@/lib/types";
import { Icon, Skeleton } from "@/components/icons";
import { ExportButtons } from "./export-buttons";
import { useMeetingPolling, useMeetings } from "./provider";

export function Status({ status }: { status: MeetingStatus }) {
  const labels = {
    QUEUED: "В очереди",
    PROCESSING: "Обработка",
    COMPLETED: "Готово",
    FAILED: "Ошибка",
  };
  return (
    <span className={`status status-${status.toLowerCase()}`}>
      <i />
      {labels[status]}
    </span>
  );
}
export function MeetingList({ compact = false }: { compact?: boolean }) {
  const { links, meetings, errors, loaded, refresh } = useMeetings();
  useMeetingPolling(links.map((link) => link.id));
  const shown = compact ? links.slice(0, 2) : links;
  if (!loaded)
    return (
      <div className="meeting-table">
        {[0, 1].map((row) => (
          <div className="meeting-row" key={row}>
            <Skeleton width="80%" />
            <Skeleton width="70%" />
            <Skeleton width="60%" />
            <Skeleton width="90%" />
          </div>
        ))}
      </div>
    );
  if (!links.length)
    return (
      <div className="empty-state">
        <Icon name="file" />
        <div>
          <h3>Здесь появятся ваши записи</h3>
          <p>Загрузите файл или запишите первую встречу.</p>
        </div>
      </div>
    );
  return (
    <div className={`meeting-table ${compact ? "compact" : ""}`}>
      {!compact && (
        <div className="table-heading" aria-hidden="true">
          <span>Название</span>
          <span>Добавлено</span>
          <span>Статус</span>
          <span>Экспорт</span>
        </div>
      )}
      {shown.map((link) => {
        const meeting = meetings[link.id];
        const error = errors[link.id];
        return (
          <article className="meeting-row" key={link.id}>
            <div className="meeting-name">
              <Link href={`/meetings/${encodeURIComponent(link.id)}`}>
                {meeting?.title ?? link.title}
              </Link>
              {!compact && (
                <p>
                  {meeting?.result
                    ? duration(meeting.result.durationMs)
                    : meeting?.status === "FAILED"
                      ? "Не удалось обработать запись"
                      : "Аудиозапись"}
                </p>
              )}
            </div>
            <time dateTime={link.createdAt}>
              {addedDate(link.createdAt)}
              {compact && meeting?.result
                ? ` · ${duration(meeting.result.durationMs)}`
                : ""}
            </time>
            <div>
              {error ? (
                <>
                  <span className="inline-error">Нет связи</span>
                  <button
                    className="text-button retry"
                    onClick={() => void refresh(link.id)}
                  >
                    Повторить
                  </button>
                </>
              ) : meeting ? (
                <Status status={meeting.status} />
              ) : (
                <Skeleton width="110px" />
              )}
            </div>
            <ExportButtons
              link={link}
              enabled={meeting?.status === "COMPLETED"}
            />
          </article>
        );
      })}
    </div>
  );
}
