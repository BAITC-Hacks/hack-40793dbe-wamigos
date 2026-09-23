# Wamigos Java backend

Java 21 / Spring Boot backend для одноразовой обработки записи совещания. Backend принимает файл, сохраняет задание в PostgreSQL, обрабатывает его фоновым worker через `MeetingAnalysisPort`, возвращает результат по секретному токену и формирует PDF/DOCX.

Текущий этап работает полностью автономно через детерминированный `MockMeetingAnalysisAdapter`. Он не отправляет аудио или транскрипт во внешние AI API и не реализует Python AI, ASR, диаризацию или LLM.

## Требования

Для запуска всего приложения одной командой используйте [корневой README](../README.md). Требования и команды ниже относятся к отдельному запуску backend из каталога `backend/`; при общем Docker-запуске API опубликован на порту 8081 вместо 8080.

- Java 21;
- Maven 3.6.3+;
- PostgreSQL;
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

Интеграционные тесты запускают локальный embedded PostgreSQL и проверяют Flyway, очередь, mock happy path/failure, токены, timeout/cleanup и оба формата экспорта.

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
| `APP_AI_MODE` | `mock` | Активный адаптер анализа |
| `APP_AI_MOCK_DELAY` | `PT0.2S` | Задержка deterministic mock |
| `APP_AI_MOCK_FORCE_FAILURE` | `false` | Принудительный `AI_PROCESSING_FAILED` |
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
- Health: `http://localhost:8080/actuator/health`

## Хранилище и очередь

PostgreSQL — единственный источник состояния очереди. Worker забирает одну `QUEUED` запись через `FOR UPDATE SKIP LOCKED`, фиксирует `PROCESSING` короткой транзакцией и вызывает анализ уже вне неё. Исходные файлы сохраняются под серверными UUID-именами в private-каталоге на persistent volume.

При ошибке создания DB job сохранённый файл компенсирующе удаляется. Cleanup сначала идемпотентно удаляет файл, затем строку БД. Активные задания не очищаются.

## Подключение реального Python adapter

Следующий интеграционный этап должен добавить отдельную реализацию `MeetingAnalysisPort`, активируемую новым значением `APP_AI_MODE`. Внутренние Python request/response DTO маппятся в `MeetingAnalysisOutput`; публичный Java → Frontend контракт не изменяется. Python adapter обязан работать внутри закрытого контура и не должен молча переключаться на внешний cloud provider.

Сторонние компоненты и лицензии перечислены в [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
