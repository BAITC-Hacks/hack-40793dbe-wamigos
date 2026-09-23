# ПРОТОКОЛ · Wamigos

Загрузите MP3/MP4 или запишите встречу в браузере, дождитесь обработки, прочитайте расшифровку, поручения и проблемы, скачайте PDF или Word.

## Какую проблему решаем

После встреч решения и поручения теряются в записи, а ручное составление протокола требует времени. «ПРОТОКОЛ» собирает расшифровку, итоги, задачи с исполнителями и сроками и озвученные проблемы в одном документе. Продукт рассчитан на рабочие встречи на русском и казахском языках; готовность реального AI-анализа описана ниже отдельно от работающей инфраструктуры.

## Технологии

- Интерфейс: Next.js, React, TypeScript; запись микрофона через MediaRecorder.
- API и фоновые задания: Java 21, Spring Boot, PostgreSQL, Flyway.
- Документы: серверный экспорт PDF и DOCX.
- AI-модуль: Python, FastAPI, FFmpeg, faster-whisper `large-v3`, публичная ECAPA speaker-embedding модель и локальный Ollama/Qwen.
- Запуск: Docker Compose, отдельные контейнеры frontend/backend/AI/Ollama/PostgreSQL и постоянные volumes моделей и данных.

## Запуск для жюри

Нужны Git, Docker с Compose v2, NVIDIA GPU и установленный NVIDIA Container Toolkit. Локальные Java, Maven, Node.js, Python, PostgreSQL и `HF_TOKEN` не нужны.

```sh
cp .env.example .env
docker compose up --build --wait
```

Откройте **[http://localhost:3000](http://localhost:3000)**. Первый запуск собирает тяжёлый Python-образ, загружает Qwen, а при первом анализе — модели Whisper и ECAPA, поэтому он заметно дольше последующих. Публичные веса скачиваются без токена и сохраняются в Docker volumes. Содержимое встреч обрабатывается локально; cloud fallback отсутствует.

Если репозиторий ещё не скачан:

```sh
git clone --branch develop https://github.com/BAITC-Hacks/hack-40793dbe-wamigos.git
cd hack-40793dbe-wamigos
cp .env.example .env
docker compose up --build --wait
```

Для приватного репозитория нужен доступ GitHub. Для первоначальной сборки нужен интернет. Содержимое встреч не отправляется внешним AI API.

## Как соединены сервисы

Frontend отправляет media только в Java API. Java сохраняет исходный файл и создаёт задание в PostgreSQL, а worker потоково отправляет его в синхронный `POST /internal/v1/analyze` Python-сервиса. Python выполняет FFmpeg → Whisper → ECAPA embeddings/clustering → speaker resolution → локальный Qwen и возвращает готовый результат. Java проверяет ссылки и интервалы, сохраняет результат и формирует PDF/DOCX.

Python не создаёт собственных jobs. Runtime mock, hardcoded result и cloud fallback отсутствуют. Если AI недоступен или вернул некорректный ответ, конкретное Java-задание переходит в `FAILED`.

## Проверка сценария

1. Выберите MP3/MP4 или нажмите «Начать запись» и разрешите микрофон.
2. Дождитесь окончания загрузки: до принятия файла сервером вкладку закрывать нельзя.
3. Java обработает запись в фоне, готовый протокол откроется автоматически.
4. Проверьте реальную расшифровку, говорящих, поручения, сроки, проблемы и summary.
5. Нажмите PDF или Word: Java сформирует документ для скачивания.
6. Откройте «Все записи» или перезагрузите страницу: последние пять ссылок остаются в этом браузере.

Лимиты: 500 МБ / 60 минут; серверная проверка окончательная. Хранение — 24 часа после завершения или ошибки. Регистрации нет: доступ даёт токен в localStorage. Очистка данных браузера удаляет ссылки. Для микрофона вне localhost нужен HTTPS.

## Адреса и управление

| Сервис | Адрес |
| --- | --- |
| Интерфейс | http://localhost:3000 |
| Java API | http://localhost:8081 |
| Swagger | http://localhost:8081/swagger-ui.html |
| Проверка Java | http://localhost:8081/actuator/health |
| Проверка Python AI | http://localhost:8000/health |

PostgreSQL и Ollama доступны только внутри Docker-сети. Java обращается к Python по `http://ai-service:8000`, Python к Ollama — по `http://ollama:11434/v1`. База, исходные файлы и веса моделей хранятся в отдельных Docker volumes. Публичные порты привязаны только к локальному компьютеру.

```sh
docker compose ps
docker compose logs --tail=100 backend ai-service ollama frontend
docker compose down
```

`down` сохраняет данные. Повторный запуск — `docker compose up --build --wait`. Для намеренного полного сброса есть `docker compose down --volumes`: эта команда безвозвратно удаляет базу и записи.

## Если AI не готов

- `DIARIZATION_UNAVAILABLE`: проверьте интернет при первой анонимной загрузке публичной ECAPA-модели и volume `huggingface-cache`.
- Ошибка доступа к GPU: проверьте `nvidia-smi` на хосте и настройку NVIDIA Container Toolkit для Docker.
- Долгий первый анализ: Whisper и ECAPA загружаются лениво; следите за `docker compose logs -f ai-service`.
- Ошибка Ollama/model not found: проверьте `docker compose logs ollama ollama-model`; сервис загрузки модели должен завершиться с кодом 0.
- Python `422`: media не декодируется, не содержит аудиодорожку или нарушает ограничения входа.
- Java `AI_PROCESSING_FAILED`: смотрите безопасный код ошибки в логах backend и соответствующую запись в логах AI; fallback намеренно не выполняется.

Для локального запуска Python вне Docker оставьте Java URL `http://localhost:8000`. В общем compose URL должен оставаться `http://ai-service:8000`: `localhost` внутри backend-контейнера указывает на сам backend.

## Если порты заняты

Скопируйте корневой `.env.example` в `.env`, задайте свободные порты и соответствующие адреса:

```dotenv
FRONTEND_PORT=3300
FRONTEND_ORIGIN=http://localhost:3300
BACKEND_PORT=8181
API_PUBLIC_URL=http://localhost:8181
AI_PORT=8100
```

Повторите команду запуска и откройте `http://localhost:3300`. `API_PUBLIC_URL` используется браузером и встраивается при сборке, поэтому после изменения необходим `--build`. `FRONTEND_ORIGIN` задаёт разрешённый CORS origin Java. Локальная frontend `.env.local` не включается в Docker-образ.

Пароль PostgreSQL по умолчанию предназначен только для локальной проверки. Настройки находятся в `.env.example`; изменение пароля уже созданной базы требует отдельного изменения в PostgreSQL, а не только правки `.env`.

## Разработка

- [Frontend](frontend/README.md): Next.js / React / TypeScript.
- [Backend](backend/README.md): Java 21 / Spring Boot / PostgreSQL, API и конфигурация.
- [AI service](ai-service/README.md): Python / FastAPI / Ollama.
- [История изменений](CHANGELOG.md).

Интеграционная ветка — `develop`. Java Dockerfile собирает JAR внутри контейнера: предварительный `mvn package` не нужен. Сборка образа не запускает backend-тесты.
