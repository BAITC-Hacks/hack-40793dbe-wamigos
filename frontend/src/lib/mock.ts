import { ApiError } from './errors';
import type { AcceptedMeeting, Meeting, MeetingLink, MeetingResult, UploadInput } from './types';

const result: MeetingResult = {
  durationMs: 24 * 60_000, summary: 'Обсудили подготовку отчёта и проблему с подключением к Wi-Fi.',
  speakers: [{ id: 's1', name: 'Арнур' }, { id: 's2', name: 'Максат' }, { id: 's3', name: 'Влад' }],
  segments: [
    { id: 'seg1', speakerId: 's1', startMs: 12_000, endMs: 20_000, text: 'Доброе утро, коллеги.', tags: [] },
    { id: 'seg2', speakerId: 's2', startMs: 28_000, endMs: 38_000, text: 'Не получается подключиться к Wi-Fi.', tags: ['PROBLEM'] },
    { id: 'seg3', speakerId: 's3', startMs: 65_000, endMs: 75_000, text: 'Арнур, подготовь отчёт до 15 октября.', tags: ['TASK'] },
  ],
  tasks: [{ id: 't1', text: 'Подготовить отчёт', assigneeName: 'Арнур', assignerName: 'Влад', deadlineRaw: 'до 15 октября', deadlineDate: null, sourceSegmentIds: ['seg3'] }],
  problems: [{ id: 'p1', text: 'У Максата не работает подключение к Wi-Fi.', reportedBy: 'Максат', sourceSegmentIds: ['seg2'] }],
};
export function demoLinks(): MeetingLink[] {
  return ['Планёрка команды', 'Обсуждение проекта', 'Совещание отдела', 'Встреча с командой', 'Запись с микрофона'].map((title, index) => ({
    id: `demo-${index}`, accessToken: 'demo-only', title, createdAt: new Date(Date.now() - index * 60_000).toISOString(), expiresAt: null,
  }));
}
export async function uploadMock(input: UploadInput): Promise<AcceptedMeeting> {
  await new Promise(resolve => setTimeout(resolve, 800));
  return { id: `mock-${Date.now()}`, accessToken: 'demo-only', title: input.file.name.replace(/\.[^.]+$/, ''), status: 'QUEUED', createdAt: new Date().toISOString(), expiresAt: null };
}
export async function getMock(link: MeetingLink): Promise<Meeting> {
  await new Promise(resolve => setTimeout(resolve, 250));
  if (!/^(demo|mock)-/.test(link.id)) throw new ApiError(404);
  const age = Date.now() - Date.parse(link.createdAt);
  const index = Number(link.id.split('-')[1]);
  const status = link.id.startsWith('demo-') ? (['COMPLETED', 'COMPLETED', 'PROCESSING', 'QUEUED', 'FAILED'] as const)[index] : age < 4000 ? 'QUEUED' : age < 11000 ? 'PROCESSING' : 'COMPLETED';
  if (!status) throw new ApiError(404);
  return { id: link.id, title: link.title, createdAt: link.createdAt, startedAt: null, timeZone: null, status,
    stage: status === 'PROCESSING' ? 'ANALYZING' : null,
    expiresAt: status === 'COMPLETED' || status === 'FAILED' ? new Date(Date.parse(link.createdAt) + 86_400_000).toISOString() : null,
    error: status === 'FAILED' ? { code: 'NO_AUDIO', message: 'Нет аудио' } : null,
    result: status === 'COMPLETED' ? result : null,
  };
}
