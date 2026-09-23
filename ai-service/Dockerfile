FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    VIRTUAL_ENV=/opt/venv \
    PATH=/opt/venv/bin:$PATH \
    LD_LIBRARY_PATH=/opt/venv/lib/python3.12/site-packages/nvidia/cublas/lib:/opt/venv/lib/python3.12/site-packages/nvidia/cudnn/lib

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && python -m venv "$VIRTUAL_ENV"

WORKDIR /app
COPY requirements.txt ./
RUN pip install --upgrade pip \
    && pip install -r requirements.txt

COPY app ./app

RUN useradd --system --uid 10001 --create-home ai-service \
    && mkdir -p /models/huggingface \
    && chown -R ai-service:ai-service /app /models/huggingface

USER ai-service
EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
