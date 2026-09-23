from pathlib import Path

from app.pipeline import MeetingPipeline
from app.schemas import MeetingAnalysisResult, MeetingContext
from app.speech.pipeline import SpeechPipeline


class AudioMeetingPipeline:
    def __init__(self, speech: SpeechPipeline, intelligence: MeetingPipeline) -> None:
        self._speech = speech
        self._intelligence = intelligence

    async def analyze_file(
        self, media_path: Path, context: MeetingContext
    ) -> MeetingAnalysisResult:
        transcript = await self._speech.transcribe(media_path)
        result = await self._intelligence.analyze_transcript(
            transcript.segments, context
        )
        return result.model_copy(
            update={
                "duration_ms": transcript.duration_ms,
                "diagnostics": result.diagnostics.model_copy(
                    update={
                        "status": "audio-analysis-complete",
                        "warnings": [
                            *result.diagnostics.warnings,
                            *transcript.warnings,
                        ],
                    }
                ),
            }
        )
