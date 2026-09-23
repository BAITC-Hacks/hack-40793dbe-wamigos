import type { ProblemDetails } from "./types";

export class ApiError extends Error {
  constructor(
    public status: number,
    public code?: string,
  ) {
    super(code ?? String(status));
  }
}

const statusMessages: Record<number, string> = {
  400: "Проверьте файл и параметры записи.",
  401: "Нет доступа к этой записи. Откройте её в браузере, где она была создана.",
  403: "Нет доступа к этой записи.",
  404: "Запись недоступна или срок её хранения истёк.",
  409: "Документ ещё не готов. Дождитесь завершения обработки.",
  413: "Файл слишком большой. Максимальный размер — 500 МБ.",
  415: "Формат не поддерживается. Выберите MP3 или MP4.",
  422: "Не удалось прочитать аудио. Проверьте, что оно есть в файле и длится не больше 60 минут.",
  503: "Очередь заполнена. Попробуйте загрузить запись позже.",
};

const codeMessages: Record<string, string> = {
  FILE_TOO_LARGE: statusMessages[413],
  UNSUPPORTED_MEDIA_TYPE: statusMessages[415],
  UNSUPPORTED_FORMAT: statusMessages[415],
  MEETING_NOT_FOUND: statusMessages[404],
  MEETING_EXPIRED: statusMessages[404],
  EXPORT_NOT_READY: statusMessages[409],
  QUEUE_FULL: statusMessages[503],
  NO_AUDIO: "В файле не найдена аудиодорожка.",
  DURATION_EXCEEDED: "Запись должна длиться не больше 60 минут.",
  MEDIA_DECODE_FAILED: "Не удалось прочитать аудио. Попробуйте другой файл.",
  MEDIA_NOT_DECODABLE: "Не удалось прочитать аудио. Попробуйте другой файл.",
  MEETING_NOT_AVAILABLE: statusMessages[404],
  NO_AUDIO_TRACK: "В файле не найдена аудиодорожка.",
  DURATION_LIMIT_EXCEEDED: "Запись должна длиться не больше 60 минут.",
  PROCESSING_INTERRUPTED:
    "Обработка прервалась после перезапуска сервера. Загрузите запись повторно.",
  PROCESSING_TIMEOUT:
    "Обработка заняла слишком много времени. Попробуйте более короткую запись.",
  AI_PROCESSING_FAILED:
    "Не удалось обработать аудио. Попробуйте загрузить другой файл.",
  EXPORT_FAILED:
    "Не удалось подготовить документ. Попробуйте скачать его ещё раз.",
};

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError)
    return (
      (error.code && codeMessages[error.code]) ||
      statusMessages[error.status] ||
      "Не удалось выполнить запрос. Попробуйте ещё раз."
    );
  return "Нет связи с сервером. Проверьте подключение и повторите запрос.";
}

export function processingError(code?: string): string {
  return (
    (code && codeMessages[code]) ||
    "Не удалось обработать запись. Попробуйте загрузить другой файл."
  );
}

export async function responseError(response: Response): Promise<ApiError> {
  const problem: ProblemDetails = await response.json().catch(() => ({}));
  return new ApiError(response.status, problem.code);
}
