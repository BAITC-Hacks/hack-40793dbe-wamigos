# Wamigos Java backend

Java 21 / Spring Boot backend для одноразовой обработки записи совещания. Backend принимает файл, сохраняет задание в PostgreSQL, передаёт его фоновым worker в Python AI service, возвращает результат по секретному токену и формирует PDF/DOCX.

Python вызывается синхронным `POST /internal/v1/analyze` с исходным media-файлом в multipart. В runtime нет mock-анализа, fallback и повторных попыток: недоступность или некорректный ответ AI service завершают конкретное задание с ошибкой, не мешая backend продолжать принимать запросы.

## Требования

Для запуска всего приложения одной командой используйте [корневой README](../README.md). Требования и команды ниже относятся к отдельному запуску backend из каталога `backend/`; при общем Docker-запуске API опубликован на порту 8081 вместо 8080.

- Java 21;
- Maven 3.6.3+;
- PostgreSQL;
- Python AI service с контрактом `POST /internal/v1/analyze` и `GET /health`;
- DejaVu Sans по пути `/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf` либо другой совместимый TTF через `APP_EXPORT_FONT_PATH`.

## Локальный запуск

Запустить PostgreSQL:

```bash
docker compose up -d postgres
```

Запустить backend:

```bash
mvn spring-boot:run
```

По умолчанию приложение доступно на `http://localhost:8080`. Flyway автоматически создаёт таблицу `meeting_jobs` с нуля.

Полная проверка:

```bash
mvn clean verify
```

Интеграционные тесты запускают локальные embedded PostgreSQL и MockWebServer. Они проверяют multipart-контракт Python, очередь, успешную обработку, media/HTTP/network/timeout/JSON/domain failures, токены, timeout/cleanup и оба формата экспорта.

## Основная конфигурация

| Переменная | По умолчанию | Назначение |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/wamigos` | JDBC URL PostgreSQL |
| `DB_USERNAME` | `wamigos` | Пользователь PostgreSQL |
| `DB_PASSWORD` | `wamigos` | Пароль PostgreSQL; для общего окружения задаётся секретом |
| `APP_STORAGE_ROOT` | `./data/meetings` | Private persistent-каталог исходных файлов |
| `APP_UPLOAD_MAX_BYTES` | `524288000` | Лимит файла 500 MB |
| `APP_UPLOAD_MAX_DURATION` | `PT60M` | Максимальная длительность анализа |
| `APP_MAX_FILE_SIZE` | `500MB` | Multipart-лимит Spring |
| `APP_MAX_REQUEST_SIZE` | `501MB` | Общий multipart-лимит Spring |
| `APP_QUEUE_CAPACITY` | `20` | Максимум ожидающих `QUEUED` заданий |
| `APP_QUEUE_POLL_INTERVAL` | `PT1S` | Интервал DB worker |
| `APP_PROCESSING_WORKER_ENABLED` | `true` | Включение worker; выключается только в специальных тестах/операциях |
| `APP_PROCESSING_TIMEOUT` | `PT30M` | Таймаут состояния `PROCESSING` |
| `APP_TIMEOUT_CHECK_INTERVAL` | `PT1M` | Интервал проверки таймаута |
| `APP_STARTUP_RECOVERY_ENABLED` | `true` | Перевод незавершённых после рестарта jobs в `PROCESSING_INTERRUPTED` |
| `APP_RETENTION` | `PT24H` | Хранение после `COMPLETED`/`FAILED` |
| `APP_CLEANUP_INTERVAL` | `PT10M` | Интервал физической очистки |
| `AI_BASE_URL` | `http://localhost:8000` | Базовый URL Python AI service |
| `AI_CONNECT_TIMEOUT` | `5s` | Таймаут соединения с Python |
| `AI_RESPONSE_TIMEOUT` | `25m` | Таймаут полного анализа одной записи |
| `AI_HEALTH_TIMEOUT` | `3s` | Отдельный короткий таймаут `GET /health` |
| `APP_FRONTEND_ORIGIN` | `http://localhost:3000` | Единственный разрешённый CORS origin |
| `APP_EXPORT_FONT_PATH` | путь DejaVu Sans | Встраиваемый PDF-шрифт с RU/KK символами |

Для сетевого окружения API публикуется только через HTTPS. Токен нельзя добавлять в URL или логи.

## API

### Создание обработки

```bash
curl -i -X POST http://localhost:8080/api/v1/meetings \
  -F 'file=@meeting.mp3' \
  -F 'source=UPLOAD' \
  -F 'title=Совещание' \
  -F 'startedAt=2026-09-23T10:00:00+06:00' \
  -F 'timeZone=Asia/Almaty'
```

Ответ `202 Accepted`:

```json
{
  "id": "42fc3437-b28b-40d5-86c7-ae4c859d0591",
  "accessToken": "opaque-secret",
  "status": "QUEUED",
  "title": "Совещание",
  "createdAt": "2026-09-23T04:00:00Z",
  "expiresAt": null
}
```

Открытый `accessToken` возвращается только один раз. В PostgreSQL сохраняется только SHA-256 hash.

### Получение статуса и результата

```bash
curl -i http://localhost:8080/api/v1/meetings/42fc3437-b28b-40d5-86c7-ae4c859d0591 \
  -H 'Authorization: Bearer opaque-secret'
```

`QUEUED`/`PROCESSING` возвращают `result: null`, `error: null`. `COMPLETED` возвращает единый результат. `FAILED` возвращается как состояние ресурса с HTTP 200 и объектом `error`.

### Экспорт

PDF:

```bash
curl -o meeting-protocol.pdf \
  'http://localhost:8080/api/v1/meetings/42fc3437-b28b-40d5-86c7-ae4c859d0591/export?format=pdf' \
  -H 'Authorization: Bearer opaque-secret'
```

DOCX:

```bash
curl -o meeting-protocol.docx \
  'http://localhost:8080/api/v1/meetings/42fc3437-b28b-40d5-86c7-ae4c859d0591/export?format=docx' \
  -H 'Authorization: Bearer opaque-secret'
```

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`; компонент `aiService` отражает доступность Python через `GET /health`

## Хранилище и очередь

PostgreSQL — единственный источник состояния очереди. Worker забирает одну `QUEUED` запись через `FOR UPDATE SKIP LOCKED`, фиксирует `PROCESSING` короткой транзакцией и вызывает анализ уже вне неё. Исходные файлы сохраняются под серверными UUID-именами в private-каталоге на persistent volume.

При ошибке создания DB job сохранённый файл компенсирующе удаляется. Cleanup сначала идемпотентно удаляет файл, затем строку БД. Активные задания не очищаются.

## Интеграция с Python AI

Backend передаёт только:

- `file` — исходный media-файл как потоковый multipart resource;
- `startedAt` — ISO offset date-time, только если время было передано клиентом;
- `timeZone` — именованная IANA-зона, только если она была передана клиентом.

В Python не отправляются access token, внутренний job ID или другие backend-данные. Ответ Python преобразуется из внутренних transport DTO в публичный результат и проверяется на корректность временных интервалов, идентификаторов и ссылок на сегменты. Поле `diagnostics` остаётся внутренним и не попадает в API.

Коды `MEDIA_NOT_DECODABLE`, `NO_AUDIO_TRACK` и `DURATION_LIMIT_EXCEEDED` из HTTP 422 сохраняются. HTTP 500/503, network/timeout, malformed JSON и некорректный доменный результат преобразуются в `AI_PROCESSING_FAILED`. Проверка health не выполняется перед каждым анализом и не блокирует запуск backend: при недоступном Python приложение стартует, health становится `DOWN`, а новые задания завершаются ошибкой обработки.

Фактическая точность распознавания, диаризации и извлечения фактов зависит от реализации Python endpoint. Публичный Java → Frontend контракт при подключении не изменяется.

Сторонние компоненты и лицензии перечислены в [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
