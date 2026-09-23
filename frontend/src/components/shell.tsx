"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { useMeetings } from "@/features/meetings/provider";
import { Motif } from "./icons";

export function Shell({ children }: { children: ReactNode }) {
  const { storageAvailable } = useMeetings();
  return (
    <div className="app-shell">
      <header className="header">
        <Link href="/" className="brand" aria-label="Протокол — на главную">
          <Motif small />
          <span>ПРОТОКОЛ</span>
        </Link>
        <span
          className="language-label"
          title="Обработка русской и казахской речи"
        >
          RU / KZ
        </span>
      </header>
      <main className="main">
        {!storageAvailable && (
          <div className="notice" role="status">
            Браузер не сохраняет ссылки на записи. После закрытия страницы
            восстановить доступ не получится.
          </div>
        )}
        {children}
      </main>
    </div>
  );
}
