import asyncio
from time import monotonic

from app.intelligence.meeting_facts_extractor import MeetingFactsExtractor
from app.schemas import (
    Diagnostics,
    MeetingAnalysisResult,
    MeetingContext,
    Segment,
    SegmentTag,
    Speaker,
    validate_segments,
)
from app.service_error import ServiceError


class MeetingPipeline:
    def __init__(self, extractor: MeetingFactsExtractor) -> None:
        self._extractor = extractor
        self._lock = asyncio.Lock()

    async def analyze_transcript(
        self,
        segments: list[Segment],
        context: MeetingContext,
    ) -> MeetingAnalysisResult:
        validate_segments(segments)
        if self._lock.locked():
            raise ServiceError(
                "ANALYSIS_BUSY", "An analysis is already running. Retry later."
            )
        async with self._lock:
            return await self._analyze(segments, context)

    async def _analyze(
        self, segments: list[Segment], context: MeetingContext
    ) -> MeetingAnalysisResult:
        started_at = monotonic()
        facts, repair_attempts = await self._extractor.extract(segments, context)
        task_sources = {
            source for task in facts.tasks for source in task.source_segment_ids
        }
        problem_sources = {
            source
            for problem in facts.problems
            for source in problem.source_segment_ids
        }
        tagged_segments = [
            segment.model_copy(
                update={
                    "tags": [
                        tag
                        for tag, sources in (
                            (SegmentTag.TASK, task_sources),
                            (SegmentTag.PROBLEM, problem_sources),
                        )
                        if segment.id in sources
                    ]
                }
            )
            for segment in segments
        ]
        speakers = self._collect_speakers(segments)
        duration_ms = max((segment.end_ms for segment in segments), default=0)
        processing_time_ms = max(0, round((monotonic() - started_at) * 1000))

        return MeetingAnalysisResult(
            duration_ms=duration_ms,
            summary="",
            speakers=speakers,
            segments=tagged_segments,
            tasks=facts.tasks,
            problems=facts.problems,
            diagnostics=Diagnostics(
                status="facts-extracted",
                processing_time_ms=processing_time_ms,
                llm_model=self._extractor.model,
                prompt_version=self._extractor.prompt_version,
                repair_attempts=repair_attempts,
                warnings=[
                    "Facts have not undergone independent verification.",
                    "Speaker identity resolution is pending; assignerName and reportedBy remain null.",
                    "Deadline normalization and summary generation are not implemented yet.",
                ],
            ),
        )

    def _collect_speakers(self, segments: list[Segment]) -> list[Speaker]:
        speaker_ids = {
            segment.speaker_id for segment in segments if segment.speaker_id is not None
        }
        return [Speaker(id=speaker_id) for speaker_id in sorted(speaker_ids)]
