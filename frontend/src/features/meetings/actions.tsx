"use client";

import { createContext, useContext, useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { useRouter } from "next/navigation";
import { uploadMeeting } from "@/lib/api";
import { config } from "@/lib/config";
import { errorMessage } from "@/lib/errors";
import { timestamp } from "@/lib/format";
import type { UploadInput } from "@/lib/types";
import { Dialog } from "@/components/dialog";
import { FileArt, Icon, Spinner } from "@/components/icons";
import { useRecorder } from "../recording/use-recorder";
import { useMeetings } from "./provider";

interface Actions {
  choose: () => void;
  record: () => void;
  busy: boolean;
  requesting: boolean;
}
const Context = createContext<Actions | null>(null);

export function ActionsProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const { add } = useMeetings();
  const input = useRef<HTMLInputElement>(null);
  const pending = useRef<UploadInput | null>(null);
  const sending = useRef(false);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState("");
  const [validation, setValidation] = useState("");
  const [confirming, setConfirming] = useState(false);
  const [filename, setFilename] = useState("");

  async function submit(value: UploadInput) {
    if (sending.current) return;
    sending.current = true;
    pending.current = value;
    setFilename(value.file.name);
    setUploadError("");
    setValidation("");
    setUploading(true);
    try {
      const accepted = await uploadMeeting(value);
      add(accepted);
      pending.current = null;
      setFilename("");
      router.push(`/meetings/${encodeURIComponent(accepted.id)}`);
    } catch (error) {
      setUploadError(
        `${errorMessage(error)} Если ответ потерялся, сервер уже мог принять запись.`,
      );
    } finally {
      sending.current = false;
      setUploading(false);
    }
  }
  const recording = useRecorder((value) => void submit(value));
  const active =
    recording.state === "recording" || recording.state === "stopping";
  const busy = uploading || active || recording.state === "requesting";

  useEffect(() => {
    if (!active && !uploading) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [active, uploading]);

  function select(file?: File) {
    if (!file || busy || sending.current) return;
    if (!file.size) {
      setValidation("Файл пустой. Выберите запись с аудио.");
      return;
    }
    if (!/\.(mp3|mp4)$/i.test(file.name)) {
      setValidation("Выберите файл в формате MP3 или MP4.");
      return;
    }
    if (file.size > config.maxBytes) {
      setValidation("Максимальный размер файла — 500 МБ.");
      return;
    }
    void submit({ file, source: "UPLOAD" });
  }

  return (
    <Context.Provider
      value={{
        choose: () => {
          if (!busy) input.current?.click();
        },
        record: () => {
          if (!busy) {
            pending.current = null;
            setUploadError("");
            void recording.start();
          }
        },
        busy,
        requesting: recording.state === "requesting",
      }}
    >
      <input
        ref={input}
        className="visually-hidden"
        type="file"
        accept=".mp3,.mp4,audio/mpeg,video/mp4"
        tabIndex={-1}
        aria-label="Выбрать MP3 или MP4"
        onChange={(event) => {
          select(event.target.files?.[0]);
          event.target.value = "";
        }}
      />
      {(validation || recording.error) && (
        <div className="notice error" role="alert">
          {validation || recording.error}
        </div>
      )}
      {children}
      {active && (
        <Dialog
          title={confirming ? "Удалить текущую запись?" : "Запись встречи"}
          onClose={() => setConfirming(true)}
        >
          {confirming ? (
            <>
              <p>Несохранённая запись будет удалена.</p>
              <div className="dialog-actions">
                <button
                  className="button yellow"
                  onClick={() => setConfirming(false)}
                >
                  Продолжить запись
                </button>
                <button
                  className="button outline"
                  onClick={() => {
                    setConfirming(false);
                    recording.stop(true);
                  }}
                >
                  Удалить запись
                </button>
              </div>
            </>
          ) : (
            <>
              <p>Предупредите участников о записи</p>
              <div className="recording-status">
                <span className="record-dot" />
                {recording.state === "stopping"
                  ? "Завершаем запись…"
                  : "Идёт запись"}
              </div>
              <div
                className="timer"
                aria-label={`Длительность записи ${timestamp(recording.elapsed)}`}
              >
                {timestamp(recording.elapsed)}
              </div>
              <button
                className="button yellow full"
                disabled={recording.state === "stopping"}
                onClick={() => recording.stop()}
              >
                <span className="stop-icon" />
                Завершить и обработать
              </button>
              <button
                className="text-button cancel-record"
                onClick={() => setConfirming(true)}
                disabled={recording.state === "stopping"}
              >
                Отменить запись
              </button>
            </>
          )}
        </Dialog>
      )}
      {(uploading || uploadError) && (
        <Dialog
          title={
            uploading ? "Загружаем запись…" : "Не удалось загрузить запись"
          }
          onClose={() => {
            if (!uploading) {
              setUploadError("");
              pending.current = null;
            }
          }}
        >
          <p className="upload-name">{filename}</p>
          {uploading ? (
            <>
              <div className="loading-symbol">
                <Spinner />
              </div>
              <p>Не закрывайте вкладку до окончания загрузки.</p>
            </>
          ) : (
            <>
              <p className="inline-error" role="alert">
                {uploadError}
              </p>
              <div className="dialog-actions">
                <button
                  className="button yellow"
                  onClick={() => {
                    if (pending.current) void submit(pending.current);
                  }}
                >
                  Повторить загрузку
                </button>
                <button
                  className="text-button"
                  onClick={() => {
                    pending.current = null;
                    setUploadError("");
                  }}
                >
                  Отмена
                </button>
              </div>
            </>
          )}
        </Dialog>
      )}
    </Context.Provider>
  );
}

export function ActionButtons({ hero = false }: { hero?: boolean }) {
  const actions = useContext(Context);
  if (!actions) throw new Error("ActionsProvider is required");
  const recordLabel = actions.requesting
    ? "Ожидаем микрофон…"
    : "Начать запись";
  if (!hero)
    return (
      <div className="action-buttons">
        <button
          className="button yellow"
          disabled={actions.busy}
          onClick={actions.choose}
        >
          <Icon name="file" />
          Загрузить файл
        </button>
        <button
          className="button dark"
          disabled={actions.busy}
          onClick={actions.record}
        >
          {actions.requesting ? <Spinner /> : <Icon name="mic" />}
          {recordLabel}
        </button>
      </div>
    );
  return (
    <div className="hero-actions">
      <section className="upload-card">
        <FileArt />
        <div className="upload-content">
          <h2>Загрузить файл</h2>
          <p>MP3 или MP4</p>
          <button
            className="button dark"
            disabled={actions.busy}
            onClick={actions.choose}
          >
            Выбрать файл
            <Icon name="arrow" />
          </button>
        </div>
      </section>
      <section className="record-card">
        <div className="mic-art">
          <Icon name="mic" />
          <span />
        </div>
        <h2>Записать встречу</h2>
        <button
          className="button paper"
          disabled={actions.busy}
          onClick={actions.record}
        >
          {actions.requesting ? <Spinner /> : null}
          {recordLabel}
          <Icon name="arrow" />
        </button>
      </section>
    </div>
  );
}
