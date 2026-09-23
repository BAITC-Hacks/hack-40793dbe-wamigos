"use client";

import { useEffect, useRef, useState } from "react";
import { exportMeeting } from "@/lib/api";
import { errorMessage } from "@/lib/errors";
import { safeFilename } from "@/lib/format";
import type { ExportFormat, MeetingLink } from "@/lib/types";
import { Icon, Spinner } from "@/components/icons";

export function ExportButtons({
  link,
  enabled,
  large = false,
}: {
  link: MeetingLink;
  enabled: boolean;
  large?: boolean;
}) {
  const [busy, setBusy] = useState<ExportFormat[]>([]);
  const active = useRef(new Set<ExportFormat>());
  const mounted = useRef(true);
  const [error, setError] = useState("");
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  async function download(format: ExportFormat) {
    if (active.current.has(format) || !enabled) return;
    active.current.add(format);
    setBusy([...active.current]);
    setError("");
    try {
      const blob = await exportMeeting(link, format);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = safeFilename(link.title, format);
      document.body.append(anchor);
      anchor.click();
      anchor.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (failure) {
      if (mounted.current) setError(errorMessage(failure));
    } finally {
      active.current.delete(format);
      if (mounted.current) setBusy([...active.current]);
    }
  }
  return (
    <div className="export-group" onClick={(event) => event.stopPropagation()}>
      <div className="export-buttons">
        {(["pdf", "docx"] as const).map((format) => (
          <button
            key={format}
            className={`button ${format === "pdf" ? "yellow" : "outline"} ${large ? "" : "small"}`}
            disabled={!enabled || busy.includes(format)}
            onClick={() => void download(format)}
            aria-label={`Скачать ${format === "pdf" ? "PDF" : "Word"}: ${link.title}`}
          >
            {busy.includes(format) ? (
              <Spinner />
            ) : large ? (
              <Icon name="file" />
            ) : null}
            {format === "pdf" ? (large ? "Скачать PDF" : "PDF") : "Word"}
          </button>
        ))}
      </div>
      {error && (
        <p className="inline-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
