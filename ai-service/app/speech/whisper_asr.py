from pathlib import Path
from typing import Any

from app.service_error import ServiceError
from app.speech.models import AsrSegment, AsrWord


class WhisperAsr:
    def __init__(self, model_name: str, device: str, compute_type: str) -> None:
        self._model_name = model_name
        self._device = device
        self._compute_type = compute_type
        self._model: Any | None = None

    def transcribe(self, audio_path: Path) -> tuple[list[AsrSegment], str | None]:
        model = self._load_model()
        try:
            generated, info = model.transcribe(
                str(audio_path),
                beam_size=5,
                temperature=0,
                vad_filter=True,
                word_timestamps=True,
            )
            language = getattr(info, "language", None)
            segments = []
            for item in generated:
                words = [
                    AsrWord(
                        text=word.word,
                        start_ms=max(0, round(word.start * 1000)),
                        end_ms=max(0, round(word.end * 1000)),
                        probability=getattr(word, "probability", None),
                    )
                    for word in (item.words or [])
                    if word.start is not None and word.end is not None
                ]
                text = item.text.strip()
                if text:
                    segments.append(
                        AsrSegment(
                            start_ms=max(0, round(item.start * 1000)),
                            end_ms=max(1, round(item.end * 1000)),
                            text=text,
                            language=language,
                            words=words,
                        )
                    )
            return segments, language
        except ServiceError:
            raise
        except Exception as error:
            raise ServiceError("ASR_FAILED", "Speech recognition failed.") from error

    def release(self) -> None:
        self._model = None

    def _load_model(self) -> Any:
        if self._model is not None:
            return self._model
        try:
            from faster_whisper import WhisperModel

            self._model = WhisperModel(
                self._model_name,
                device=self._device,
                compute_type=self._compute_type,
            )
            return self._model
        except Exception as error:
            raise ServiceError(
                "ASR_UNAVAILABLE",
                "faster-whisper model could not be loaded.",
            ) from error
