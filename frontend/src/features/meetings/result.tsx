'use client';

import Link from 'next/link';
import { Icon, Skeleton, Spinner } from '@/components/icons';
import { addedDate, calendarDate, duration, timestamp } from '@/lib/format';
import { processingError } from '@/lib/errors';
import { ActionButtons } from './actions';
import { ExportButtons } from './export-buttons';
import { useMeetingPolling, useMeetings } from './provider';

export function MeetingPage({ id }: { id: string }) {
  const { links, meetings, errors, missing, loaded, refresh } = useMeetings();
  const link = links.find(item => item.id === id);
  const meeting = meetings[id];
  useMeetingPolling(link ? [id] : []);
  const error = errors[id];
  if (!loaded || (link && !meeting && !error)) return <div className="result-skeleton" aria-label="Загружаем запись" aria-busy="true"><Skeleton width="55%" /><Skeleton width="35%" /><div className="skeleton-summary"><Skeleton /></div><div className="result-grid"><div className="skeleton-panel"><Skeleton /><Skeleton /><Skeleton /></div><div className="skeleton-panel"><Skeleton /><Skeleton /></div></div></div>;
  if (!link || missing.has(id)) return <div className="waiting"><Icon name="alert" /><h1>Запись недоступна</h1><p>Срок хранения истёк или в этом браузере нет ссылки доступа.</p><Link href="/meetings" className="button outline">Все записи</Link></div>;
  const connection = error && <div className="notice error" role="alert">{error}<button className="text-button" onClick={() => void refresh(id)}>Повторить запрос</button></div>;
  if (!meeting) return <>{connection}<div className="waiting"><h1>Не удалось загрузить запись</h1><p>Ссылка сохранена. Повторите запрос, когда связь восстановится.</p></div></>;
  if (meeting.status === 'QUEUED' || meeting.status === 'PROCESSING') {
    const stages = { PREPARING_MEDIA: 'Подготавливаем аудио', ANALYZING: 'Распознаём речь и составляем протокол', FINALIZING: 'Сохраняем результат' };
    return <>{connection}<div className="waiting"><div className="waiting-icon"><Spinner /></div><p className="waiting-title">{meeting.title}</p><h1>{meeting.status === 'QUEUED' ? 'Запись в очереди' : 'Обрабатываем запись'}</h1>{meeting.stage && <p>{stages[meeting.stage]}</p>}<p>Можно закрыть вкладку — обработка продолжится.</p><Link className="button outline" href="/meetings">Все записи<Icon name="arrow" /></Link></div></>;
  }
  if (meeting.status === 'FAILED') return <>{connection}<div className="waiting"><Icon name="alert" /><h1>Не удалось обработать запись</h1><p>{processingError(meeting.error?.code)}</p><ActionButtons /></div></>;
  const result = meeting.result;
  if (!result) return <div className="waiting"><h1>Результат недоступен</h1><p>Сервер не вернул содержимое протокола.</p><button className="button outline" onClick={() => void refresh(id)}>Повторить запрос</button></div>;
  return <>{connection}<div className="result-heading"><div><h1>{meeting.title}</h1><p className="muted">{meeting.startedAt ? addedDate(meeting.startedAt) : `Добавлено ${addedDate(meeting.createdAt)}`} · {duration(result.durationMs)}</p></div><ExportButtons link={link} enabled large /></div>
    <section className="summary"><Icon name="file" /><div><h2>Итоги</h2><p>{result.summary || 'Краткие итоги не указаны.'}</p></div></section>
    <div className="result-grid"><section className="transcript panel"><h2>Расшифровка</h2>{result.segments.length ? result.segments.map(segment => {
      const index = result.speakers.findIndex(speaker => speaker.id === segment.speakerId);
      const name = index >= 0 ? result.speakers[index].name?.trim() || `Говорящий ${index + 1}` : 'Неизвестный говорящий';
      return <article className="segment" key={segment.id} id={`segment-${segment.id}`}><time>{timestamp(segment.startMs)}</time><div><div className="segment-heading"><h3>{name}</h3><div className="tags">{segment.tags.map(tag => <span key={tag} className={`tag tag-${tag.toLowerCase()}`}>{tag === 'TASK' ? 'Поручение' : 'Проблема'}</span>)}</div></div><p>{segment.text}</p></div></article>;
    }) : <p className="empty-copy">Расшифровка отсутствует.</p>}</section>
    <aside className="facts"><section className="panel task-panel"><h2><Icon name="list" />Поручения</h2>{result.tasks.length ? result.tasks.map(task => <article className="fact" key={task.id}><h3>{task.text}</h3><dl><dt>Исполнитель:</dt><dd>{task.assigneeName || 'Не указан'}</dd><dt>Поручил:</dt><dd>{task.assignerName || 'Не указан'}</dd><dt>Срок:</dt><dd>{task.deadlineRaw || 'Не указан'}</dd>{task.deadlineDate && <><dt>Дата:</dt><dd>{calendarDate(task.deadlineDate)}</dd></>}</dl></article>) : <p className="empty-copy">Поручений не найдено.</p>}</section>
    <section className="panel problem-panel"><h2><Icon name="alert" />Проблемы</h2>{result.problems.length ? result.problems.map(problem => <article className="fact" key={problem.id}><p>{problem.text}</p>{problem.reportedBy && <p className="muted reporter">Сообщил: {problem.reportedBy}</p>}</article>) : <p className="empty-copy">Проблем не найдено.</p>}</section></aside></div>
  </>;
}
