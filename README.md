# ПРОТОКОЛ · Wamigos

Локальный сервис для протокола встречи: загружает MP3/MP4 или записывает звук в браузере, распознаёт речь, выделяет участников, поручения и проблемы, формирует PDF/DOCX. Аудио и текст не отправляются во внешние AI API.

## Запуск для жюри

1. Скачайте репозиторий (на GitHub: **Code → Download ZIP**) и распакуйте его. Для приватного репозитория нужен доступ к нему.
2. Установите и запустите [Docker Desktop](https://docs.docker.com/get-started/get-docker/) (Windows/macOS) либо Docker Engine с [Compose plugin](https://docs.docker.com/compose/install/linux/) (Linux). Дождитесь, пока Docker запустится.
3. Откройте терминал в распакованной папке, где лежит `compose.yaml` (на Windows подойдёт PowerShell), и выполните:

```sh
docker compose up --build --wait
```

Откройте **http://localhost:3000**. Всё остальное — Java, Python, PostgreSQL, Ollama и модели — Docker запустит сам. Отдельно устанавливать Ollama, Java, Python или создавать `.env` не нужно. Для первой сборки и загрузки публичных моделей нужен интернет и свободное место на диске (ориентир — 30 ГБ); учётные записи и API-ключи не нужны. Модели сохраняются локально. На CPU первый анализ может идти долго; для проверки возьмите короткую запись (10–20 секунд) с разборчивой речью. GPU не обязателен.

**Если есть NVIDIA GPU** и [доступ к GPU из Docker](https://docs.docker.com/compose/how-tos/gpu-support/), вместо команды выше можно запустить ускоренный режим:

```sh
docker compose -f compose.yaml -f compose.gpu.yaml up --build --wait
```

## Как проверить

Загрузите запись или запишите её микрофоном → дождитесь статуса «Готово» → проверьте расшифровку, говорящих, поручения и проблемы → скачайте PDF или Word. До конца загрузки не закрывайте вкладку. Лимит: 500 МБ и 60 минут; записи удаляются через 24 часа. Регистрация не требуется.

Если порт занят, создайте `.env` по [образцу](.env.example): для 3000 поменяйте `FRONTEND_PORT` и `FRONTEND_ORIGIN`, для 8081 — `BACKEND_PORT` и `API_PUBLIC_URL`, для 8000 — `AI_PORT`. Затем повторите запуск. Если что-то не стартовало, посмотрите `docker compose ps` и `docker compose logs --tail=100`. Остановить сервисы: `docker compose down` (данные сохраняются).

## Что внутри

Next.js → Java 21 / Spring Boot → PostgreSQL и Python / FastAPI → локальные faster-whisper, SpeechBrain ECAPA и Ollama / Qwen. Обработка идёт в контейнерах на компьютере проверяющего; готовый публичный сервер для проверки не требуется.

Подробнее: [frontend](frontend/README.md) · [backend](backend/README.md) · [AI service](ai-service/README.md) · [изменения](CHANGELOG.md).
