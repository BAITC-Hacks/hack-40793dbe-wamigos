import asyncio
import gc
from pathlib import Path
from tempfile import TemporaryDirectory

from app.service_error import ServiceError
from app.speech.audio_preprocessor import AudioPreprocessor
from app.speech.diarization import SpeakerDiarizer
from app.speech.models import SpeechTranscript
from app.speech.transcript_builder import TranscriptBuilder
from app.speech.whisper_asr import WhisperAsr


class SpeechPipeline:
    def __init__(
        self,
        preprocessor: AudioPreprocessor,
        asr: WhisperAsr,
        diarizer: SpeakerDiarizer,
        release_models_after_run: bool,
    ) -> None:
        self._preprocessor = preprocessor
        self._asr = asr
        self._diarizer = diarizer
        self._release_models_after_run = release_models_after_run
        self._lock = asyncio.Lock()

    async def transcribe(self, media_path: Path) -> SpeechTranscript:
        if self._lock.locked():
            raise ServiceError(
                "ANALYSIS_BUSY", "A speech analysis is already running. Retry later."
            )
        async with self._lock:
            return await asyncio.to_thread(self._transcribe_sync, media_path)

    def _transcribe_sync(self, media_path: Path) -> SpeechTranscript:
        try:
            with TemporaryDirectory(prefix="hackalem-speech-") as directory:
                normalized = Path(directory) / "audio.wav"
                duration_ms = self._preprocessor.normalize(media_path, normalized)
                asr_segments, language = self._asr.transcribe(normalized)
                if not asr_segments:
                    raise ServiceError("SPEECH_EMPTY", "No speech was recognized.")
                if self._release_models_after_run:
                    self._asr.release()
                    self._free_cuda_memory()
                turns = self._diarizer.diarize(normalized, asr_segments)
                if not turns:
                    raise ServiceError(
                        "DIARIZATION_EMPTY", "No speaker turns were detected."
                    )
                segments = TranscriptBuilder().build(asr_segments, turns)
                if not segments:
                    raise ServiceError("SPEECH_EMPTY", "No transcript was produced.")
                return SpeechTranscript(
                    duration_ms=max(duration_ms, segments[-1].end_ms),
                    segments=segments,
                    detected_language=language,
                )
        finally:
            if self._release_models_after_run:
                self._asr.release()
                self._diarizer.release()
                self._free_cuda_memory()

    def _free_cuda_memory(self) -> None:
        gc.collect()
        try:
            import torch

            if torch.cuda.is_available():
                torch.cuda.empty_cache()
        except ImportError:
            pass
