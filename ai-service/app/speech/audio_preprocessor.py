import json
import subprocess
from pathlib import Path

from app.service_error import ServiceError


class AudioPreprocessor:
    def __init__(self, ffmpeg_path: str, ffprobe_path: str) -> None:
        self._ffmpeg = ffmpeg_path
        self._ffprobe = ffprobe_path

    def normalize(self, source: Path, destination: Path) -> int:
        if not source.is_file():
            raise ServiceError("MEDIA_NOT_FOUND", "Input media file does not exist.")
        try:
            probe = subprocess.run(
                [
                    self._ffprobe,
                    "-v",
                    "error",
                    "-show_entries",
                    "format=duration:stream=codec_type",
                    "-of",
                    "json",
                    str(source),
                ],
                check=True,
                capture_output=True,
                text=True,
                timeout=30,
            )
            metadata = json.loads(probe.stdout)
        except (FileNotFoundError, subprocess.TimeoutExpired) as error:
            raise ServiceError(
                "SPEECH_NOT_CONFIGURED", "FFmpeg/ffprobe is not available."
            ) from error
        except (subprocess.CalledProcessError, json.JSONDecodeError) as error:
            raise ServiceError(
                "MEDIA_NOT_DECODABLE", "The input media could not be decoded."
            ) from error
        if not any(
            stream.get("codec_type") == "audio"
            for stream in metadata.get("streams", [])
        ):
            raise ServiceError("NO_AUDIO_TRACK", "The input media has no audio track.")
        try:
            subprocess.run(
                [
                    self._ffmpeg,
                    "-nostdin",
                    "-v",
                    "error",
                    "-y",
                    "-i",
                    str(source),
                    "-vn",
                    "-ac",
                    "1",
                    "-ar",
                    "16000",
                    "-c:a",
                    "pcm_s16le",
                    str(destination),
                ],
                check=True,
                capture_output=True,
                timeout=600,
            )
        except FileNotFoundError as error:
            raise ServiceError("SPEECH_NOT_CONFIGURED", "FFmpeg is not available.") from error
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
            raise ServiceError(
                "MEDIA_NOT_DECODABLE", "Audio normalization failed."
            ) from error
        duration = metadata.get("format", {}).get("duration")
        return max(0, round(float(duration or 0) * 1000))
