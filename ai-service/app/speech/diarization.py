import os
import wave
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np

from app.service_error import ServiceError
from app.speech.models import AsrSegment, SpeakerTurn


@dataclass(frozen=True)
class _SpeechChunk:
    start_ms: int
    end_ms: int


class SpeakerDiarizer:
    def __init__(
        self,
        model_name: str,
        model_path: str | None,
        device: str,
        similarity_threshold: float,
        max_speakers: int,
    ) -> None:
        self._source = model_path or model_name
        self._device = device
        self._similarity_threshold = similarity_threshold
        self._max_speakers = max_speakers
        self._classifier: Any | None = None
        self._is_local = model_path is not None

    def diarize(
        self, audio_path: Path, asr_segments: list[AsrSegment]
    ) -> list[SpeakerTurn]:
        chunks = self._speech_chunks(asr_segments)
        if not chunks:
            return []
        waveform = self._load_waveform(audio_path)
        classifier = self._load_classifier()
        try:
            embeddings = [
                self._embedding(classifier, waveform, chunk) for chunk in chunks
            ]
            labels = self._cluster(embeddings)
            return [
                SpeakerTurn(
                    speaker_id=f"SPEAKER_{label:02d}",
                    start_ms=chunk.start_ms,
                    end_ms=max(chunk.start_ms + 1, chunk.end_ms),
                )
                for chunk, label in zip(chunks, labels, strict=True)
            ]
        except ServiceError:
            raise
        except Exception as error:
            raise ServiceError(
                "DIARIZATION_FAILED", "Speaker diarization failed."
            ) from error

    def release(self) -> None:
        self._classifier = None

    def _load_classifier(self) -> Any:
        if self._classifier is not None:
            return self._classifier
        try:
            from speechbrain.inference.classifiers import EncoderClassifier

            kwargs: dict[str, Any] = {
                "source": self._source,
                "run_opts": {"device": self._device},
            }
            cache_root = os.getenv("HF_HOME")
            if cache_root and not self._is_local:
                model_dir = self._source.replace("/", "--")
                kwargs["savedir"] = str(Path(cache_root) / "speechbrain" / model_dir)
            self._classifier = EncoderClassifier.from_hparams(**kwargs)
            return self._classifier
        except Exception as error:
            raise ServiceError(
                "DIARIZATION_UNAVAILABLE",
                "The public speaker embedding model could not be loaded.",
            ) from error

    def _load_waveform(self, audio_path: Path) -> Any:
        import torch

        with wave.open(str(audio_path), "rb") as audio:
            if (
                audio.getnchannels() != 1
                or audio.getframerate() != 16000
                or audio.getsampwidth() != 2
            ):
                raise ServiceError(
                    "DIARIZATION_FAILED",
                    "Diarization input must be mono 16 kHz PCM16 WAV.",
                )
            samples = np.frombuffer(
                audio.readframes(audio.getnframes()), dtype="<i2"
            ).astype(np.float32)
        return torch.from_numpy(samples / 32768.0)

    def _embedding(
        self, classifier: Any, waveform: Any, chunk: _SpeechChunk
    ) -> np.ndarray:
        import torch

        sample_rate = 16000
        start = min(len(waveform), chunk.start_ms * sample_rate // 1000)
        end = min(len(waveform), chunk.end_ms * sample_rate // 1000)
        clip = waveform[start:end]
        minimum_samples = sample_rate
        if len(clip) < minimum_samples:
            clip = torch.nn.functional.pad(clip, (0, minimum_samples - len(clip)))
        with torch.inference_mode():
            encoded = classifier.encode_batch(clip.unsqueeze(0).to(self._device))
        vector = encoded.squeeze().detach().float().cpu().numpy()
        norm = float(np.linalg.norm(vector))
        if norm == 0:
            raise ServiceError(
                "DIARIZATION_FAILED", "Speaker embedding was empty."
            )
        return vector / norm

    def _cluster(self, embeddings: list[np.ndarray]) -> list[int]:
        centroids: list[np.ndarray] = []
        counts: list[int] = []
        labels: list[int] = []
        for embedding in embeddings:
            similarities = [float(np.dot(embedding, value)) for value in centroids]
            best = int(np.argmax(similarities)) if similarities else -1
            previous = labels[-1] if labels else -1
            previous_similarity = (
                similarities[previous] if previous >= 0 else float("-inf")
            )
            if similarities and (
                similarities[best] >= self._similarity_threshold
                or previous_similarity >= self._similarity_threshold - 0.08
                or len(centroids) >= self._max_speakers
            ):
                use_previous = (
                    previous_similarity >= self._similarity_threshold - 0.08
                )
                label = previous if use_previous else best
                centroid = centroids[label] * counts[label] + embedding
                centroids[label] = centroid / np.linalg.norm(centroid)
                counts[label] += 1
            else:
                label = len(centroids)
                centroids.append(embedding)
                counts.append(1)
            labels.append(label)
        return labels

    def _speech_chunks(self, segments: list[AsrSegment]) -> list[_SpeechChunk]:
        chunks: list[_SpeechChunk] = []
        for segment in segments:
            if segment.words:
                start = segment.words[0].start_ms
                end = segment.words[0].end_ms
                for word in segment.words[1:]:
                    if word.start_ms - end > 700 or word.end_ms - start > 3000:
                        chunks.append(_SpeechChunk(start, end))
                        start = word.start_ms
                    end = word.end_ms
                chunks.append(_SpeechChunk(start, end))
            else:
                start = segment.start_ms
                while start < segment.end_ms:
                    end = min(start + 3000, segment.end_ms)
                    chunks.append(_SpeechChunk(start, end))
                    start = end
        return chunks
