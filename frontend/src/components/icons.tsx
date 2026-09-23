import type { CSSProperties } from "react";

export function Icon({
  name,
  className = "",
}: {
  name:
    "mic" | "file" | "arrow" | "back" | "download" | "list" | "alert" | "close";
  className?: string;
}) {
  const paths = {
    mic: (
      <>
        <rect x="9" y="2" width="6" height="13" rx="3" />
        <path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3m-4 0h8" />
      </>
    ),
    file: (
      <>
        <path d="M5 2h9l5 5v15H5zM14 2v6h5M9 12h6m-6 4h6" />
      </>
    ),
    arrow: <path d="M3 12h18m-7-7 7 7-7 7" />,
    back: <path d="M21 12H3m7-7-7 7 7 7" />,
    download: <path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5" />,
    list: (
      <>
        <path d="M9 5h12M9 12h8M9 19h5" />
        <circle cx="3" cy="5" r="1" />
        <circle cx="3" cy="12" r="1" />
        <circle cx="3" cy="19" r="1" />
      </>
    ),
    alert: (
      <>
        <circle cx="12" cy="12" r="10" />
        <path d="M12 6v7m0 4v.1" />
      </>
    ),
    close: <path d="m5 5 14 14M19 5 5 19" />,
  };
  return (
    <svg
      className={`icon ${className}`}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name]}
    </svg>
  );
}
export function Spinner() {
  return <span className="spinner" aria-hidden="true" />;
}
export function Motif({ small = false }: { small?: boolean }) {
  return (
    <span className={`motif ${small ? "motif-small" : ""}`} aria-hidden="true">
      <i />
      <i />
      <i />
      <i />
    </span>
  );
}
export function FileArt() {
  return (
    <div className="file-art" aria-hidden="true">
      <svg viewBox="0 0 160 180">
        <path
          d="M38 8h67l40 42v120H38z"
          fill="var(--paper)"
          stroke="currentColor"
          strokeWidth="2"
        />
        <path
          d="M105 8v43h40"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
        />
        <rect x="0" y="45" width="72" height="64" fill="var(--ink)" />
        <rect x="37" y="45" width="36" height="64" fill="var(--sage)" />
        <rect x="74" y="109" width="30" height="30" fill="var(--yellow)" />
      </svg>
    </div>
  );
}
export function Skeleton({ width }: { width?: string }) {
  return (
    <span
      className="skeleton"
      style={{ "--skeleton-width": width ?? "100%" } as CSSProperties}
    />
  );
}
