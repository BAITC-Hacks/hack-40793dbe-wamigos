import Link from 'next/link';
import { Motif, Icon } from '@/components/icons';
import { ActionButtons } from '@/features/meetings/actions';
import { MeetingList } from '@/features/meetings/list';

export default function Meetings() {
  return <><Link className="back-link" href="/"><Icon name="back" />На главную</Link><div className="list-heading"><div><h1>Все записи</h1><p>Последние 5 записей в этом браузере</p></div><div className="list-actions"><Motif /><ActionButtons /></div></div><MeetingList />
    <footer className="list-footer"><p>Можно закрыть вкладку — обработка продолжится.</p><p className="muted">Записи доступны 24 часа после обработки.</p></footer></>;
}
