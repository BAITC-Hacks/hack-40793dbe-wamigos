from pathlib import Path
from typing import Any

from app.service_error import ServiceError
from app.speech.models import SpeakerTurn


class SpeakerDiarizer:
    def __init__(
        self,
        model_name: str,
        model_path: str | None,
        token: str | None,
        device: str,
    ) -> None:
        self._source = model_path or model_name
        self._token = token or None
        self._device = device
        self._pipeline: Any | None = None
        self._is_local = model_path is not None

    def diarize(self, audio_path: Path) -> list[SpeakerTurn]:
        pipeline = self._load_pipeline()
        try:
            output = pipeline(str(audio_path))
            annotation = getattr(output, "exclusive_speaker_diarization", None)
            if annotation is None:
                annotation = getattr(output, "speaker_diarization", output)
            turns = [
                SpeakerTurn(
                    speaker_id=str(speaker),
                    start_ms=max(0, round(turn.start * 1000)),
                    end_ms=max(1, round(turn.end * 1000)),
                )
                for turn, _, speaker in annotation.itertracks(yield_label=True)
            ]
            return sorted(turns, key=lambda turn: (turn.start_ms, turn.end_ms))
        except ServiceError:
            raise
        except Exception as error:
            raise ServiceError("DIARIZATION_FAILED", "Speaker diarization failed.") from error

    def release(self) -> None:
        self._pipeline = None

    def _load_pipeline(self) -> Any:
        if self._pipeline is not None:
            return self._pipeline
        if not self._is_local and not self._token:
            raise ServiceError(
                "DIARIZATION_NOT_CONFIGURED",
                "HF_TOKEN or a local diarization model path is required.",
            )
        try:
            import torch
            from pyannote.audio import Pipeline

            kwargs = {} if self._is_local else {"token": self._token}
            self._pipeline = Pipeline.from_pretrained(self._source, **kwargs)
            if self._pipeline is None:
                raise RuntimeError("Model access was not granted")
            self._pipeline.to(torch.device(self._device))
            return self._pipeline
        except Exception as error:
            raise ServiceError(
                "DIARIZATION_UNAVAILABLE",
                "pyannote diarization model could not be loaded; verify model access and token.",
            ) from error
