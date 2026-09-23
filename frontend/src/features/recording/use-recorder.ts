'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { config } from '@/lib/config';
import type { UploadInput } from '@/lib/types';

const formats = [
  { mime: 'audio/webm;codecs=opus', extension: 'webm' },
  { mime: 'audio/ogg;codecs=opus', extension: 'ogg' },
  { mime: 'audio/mp4;codecs=mp4a.40.2', extension: 'm4a' },
  { mime: 'audio/mp4', extension: 'm4a' },
  { mime: 'audio/mpeg', extension: 'mp3' },
];

export function useRecorder(onReady: (input: UploadInput) => void) {
  const [state, setState] = useState<'idle' | 'requesting' | 'recording' | 'stopping'>('idle');
  const [elapsed, setElapsed] = useState(0);
  const [error, setError] = useState('');
  const recorder = useRef<MediaRecorder | null>(null);
  const stream = useRef<MediaStream | null>(null);
  const chunks = useRef<Blob[]>([]);
  const cancelled = useRef(false);
  const requesting = useRef(false);
  const alive = useRef(true);
  const started = useRef(0);
  const callback = useRef(onReady);
  callback.current = onReady;
  const release = useCallback(() => { stream.current?.getTracks().forEach(track => track.stop()); stream.current = null; }, []);
  const stop = useCallback((discard = false) => {
    cancelled.current = discard;
    const current = recorder.current;
    if (current && current.state !== 'inactive') { setState('stopping'); current.stop(); }
    release();
  }, [release]);

  useEffect(() => {
    alive.current = true;
    return () => { alive.current = false; cancelled.current = true; if (recorder.current?.state === 'recording') recorder.current.stop(); release(); };
  }, [release]);

  useEffect(() => {
    if (state !== 'recording') return;
    const interval = setInterval(() => { const ms = Date.now() - started.current; setElapsed(ms); if (ms >= config.maxRecordingMs) stop(); }, 250);
    return () => clearInterval(interval);
  }, [state, stop]);

  async function start() {
    if (requesting.current || recorder.current?.state === 'recording') return;
    setError('');
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') { setError('Запись недоступна в этом браузере. Используйте HTTPS или загрузите MP3/MP4.'); return; }
    const format = formats.find(item => MediaRecorder.isTypeSupported(item.mime));
    if (!format) { setError('Браузер не поддерживает запись в подходящем формате. Загрузите MP3 или MP4.'); return; }
    requesting.current = true; setState('requesting');
    try {
      const media = await navigator.mediaDevices.getUserMedia({ audio: true });
      if (!alive.current) { media.getTracks().forEach(track => track.stop()); return; }
      stream.current = media;
      const current = new MediaRecorder(media, { mimeType: format.mime });
      recorder.current = current; chunks.current = []; cancelled.current = false;
      const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
      let bytes = 0;
      current.ondataavailable = event => { if (event.data.size) { chunks.current.push(event.data); bytes += event.data.size; if (bytes > config.maxBytes && current.state === 'recording') { setError('Достигнут предел размера записи — 500 МБ. Запишите более короткую встречу.'); stop(true); } } };
      current.onerror = () => { cancelled.current = true; setError('Запись прервалась. Проверьте микрофон и попробуйте ещё раз.'); if (current.state !== 'inactive') current.stop(); release(); if (alive.current) setState('idle'); };
      current.onstop = () => {
        release(); recorder.current = null;
        if (!alive.current) return;
        setState('idle');
        const data = chunks.current; chunks.current = [];
        if (cancelled.current) return;
        const blob = new Blob(data, { type: current.mimeType });
        if (!blob.size) { setError('Микрофон не записал аудио. Попробуйте ещё раз.'); return; }
        if (blob.size > config.maxBytes) { setError('Запись превышает 500 МБ. Запишите более короткую встречу.'); return; }
        callback.current({ file: new File([blob], `Запись встречи.${format.extension}`, { type: blob.type }), source: 'MICROPHONE', startedAt: new Date(started.current).toISOString(), timeZone });
      };
      current.start(1000); started.current = Date.now(); setElapsed(0); setState('recording');
    } catch (failure) {
      release();
      if (alive.current) { setState('idle'); const name = failure instanceof DOMException ? failure.name : ''; setError(name === 'NotAllowedError' ? 'Разрешите доступ к микрофону в настройках браузера и попробуйте ещё раз или загрузите файл.' : name === 'NotFoundError' ? 'Микрофон не найден. Подключите его или загрузите файл.' : 'Не удалось включить микрофон. Проверьте, что он доступен, и попробуйте ещё раз.'); }
    } finally { requesting.current = false; }
  }
  return { state, elapsed, error, start, stop };
}
