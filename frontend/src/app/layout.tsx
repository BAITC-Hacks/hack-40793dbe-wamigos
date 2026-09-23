import type { Metadata } from "next";
import type { ReactNode } from "react";
import { MeetingsProvider } from "@/features/meetings/provider";
import { ActionsProvider } from "@/features/meetings/actions";
import { Shell } from "@/components/shell";
import "./globals.css";

export const metadata: Metadata = {
  title: "ПРОТОКОЛ — из разговора в протокол",
  description:
    "Загрузите запись или запишите встречу. Получите расшифровку, поручения и итоги.",
  robots: { index: false, follow: false },
};
export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ru">
      <body>
        <MeetingsProvider>
          <Shell>
            <ActionsProvider>{children}</ActionsProvider>
          </Shell>
        </MeetingsProvider>
      </body>
    </html>
  );
}
