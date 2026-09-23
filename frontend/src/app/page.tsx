import Link from "next/link";
import { Motif, Icon } from "@/components/icons";
import { ActionButtons } from "@/features/meetings/actions";
import { MeetingList } from "@/features/meetings/list";

export default function Home() {
  return (
    <>
      <section className="hero">
        <div>
          <h1>
            Из разговора —<br />в протокол.
          </h1>
          <p>Загрузите запись или запишите встречу здесь.</p>
        </div>
        <Motif />
      </section>
      <ActionButtons hero />
      <section className="recent">
        <div className="section-heading">
          <h2>Последние записи</h2>
          <Link href="/meetings">
            Все записи
            <Icon name="arrow" />
          </Link>
        </div>
        <MeetingList compact />
      </section>
      <footer className="footer">
        <p>Без регистрации. Записи доступны 24 часа после обработки.</p>
        <Motif small />
      </footer>
    </>
  );
}
