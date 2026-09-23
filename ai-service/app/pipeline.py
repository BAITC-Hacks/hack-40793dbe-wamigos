import asyncio
from time import monotonic

from app.intelligence.deadline_normalizer import DeadlineNormalizer
from app.intelligence.deadline_raw_validator import DeadlineRawValidator
from app.intelligence.evidence_binder import EvidenceBinder
from app.intelligence.fact_evidence import validate_fact_evidence
from app.intelligence.meeting_facts_extractor import MeetingFactsExtractor
from app.intelligence.segment_tagger import SegmentTagger
from app.intelligence.speaker_resolver import SpeakerResolver
from app.intelligence.speaker_attribution import SpeakerAttribution
from app.intelligence.summary import SummaryGenerator
from app.intelligence.task_verifier import TaskVerifier
from app.schemas import (
    Diagnostics,
    MeetingAnalysisResult,
    MeetingContext,
    Problem,
    Segment,
    Task,
    validate_segments,
)
from app.service_error import ServiceError


class MeetingPipeline:
    def __init__(
        self,
        extractor: MeetingFactsExtractor,
        verifier: TaskVerifier,
        summary: SummaryGenerator,
    ) -> None:
        self._extractor = extractor
        self._verifier = verifier
        self._summary = summary
        self._lock = asyncio.Lock()

    async def analyze_transcript(
        self, segments: list[Segment], context: MeetingContext
    ) -> MeetingAnalysisResult:
        validate_segments(segments)
        if self._lock.locked():
            raise ServiceError(
                "ANALYSIS_BUSY", "An analysis is already running. Retry later."
            )
        async with self._lock:
            return await self._analyze(
                sorted(
                    segments, key=lambda segment: (segment.start_ms, segment.end_ms)
                ),
                context,
            )

    async def _analyze(
        self, segments: list[Segment], context: MeetingContext
    ) -> MeetingAnalysisResult:
        started_at = monotonic()
        stage_times = {}
        repairs = {}
        speakers = SpeakerResolver().resolve(segments)
        facts, repairs["extraction"] = await self._extractor.extract(
            segments, context, speakers
        )
        facts, first_binding = EvidenceBinder().bind(facts, segments)
        facts.tasks, facts.problems = SpeakerAttribution().apply(
            facts.tasks, facts.problems, segments, speakers
        )
        grounded_problems = []
        semantic_rejections = 0
        for problem in facts.problems:
            try:
                validate_fact_evidence([], [problem], segments, speakers)
                grounded_problems.append(problem)
            except ValueError:
                semantic_rejections += 1
        facts.problems = grounded_problems
        facts.tasks, invalid_deadlines = DeadlineRawValidator().clean(
            facts.tasks, segments
        )
        stage_times["extraction"] = round((monotonic() - started_at) * 1000)
        stage_start = monotonic()
        accepted, counts, repairs["verification"] = await self._verifier.verify(
            facts.tasks, segments, speakers
        )
        verified_facts, second_binding = EvidenceBinder().bind(
            facts.model_copy(update={"tasks": accepted, "problems": []}), segments
        )
        accepted, _ = SpeakerAttribution().apply(
            verified_facts.tasks, [], segments, speakers
        )
        accepted, discarded_after_verification = DeadlineRawValidator().clean(
            accepted, segments
        )
        grounded_tasks = []
        for task in accepted:
            try:
                validate_fact_evidence([task], [], segments, speakers)
                grounded_tasks.append(task)
            except ValueError:
                semantic_rejections += 1
        accepted = grounded_tasks
        invalid_deadlines += discarded_after_verification
        stage_times["verification"] = round((monotonic() - stage_start) * 1000)
        normalizer = DeadlineNormalizer()
        tasks = [
            Task.model_validate(
                {
                    **task.model_dump(exclude={"evidence_quotes"}),
                    "deadline_date": normalizer.normalize(task.deadline_raw, context),
                }
            )
            for task in accepted
        ]
        problems = [
            Problem.model_validate(problem.model_dump(exclude={"evidence_quotes"}))
            for problem in facts.problems
        ]
        validate_fact_evidence(tasks, problems, segments, speakers)
        stage_start = monotonic()
        summary, repairs["summary"] = await self._summary.generate(
            tasks, problems
        )
        stage_times["summary"] = round((monotonic() - stage_start) * 1000)
        warnings = []
        for status in ("REJECTED", "REVIEW_REQUIRED"):
            if counts.get(status):
                warnings.append(f"{counts[status]} {status} task candidates excluded.")
        if any(speaker.name is None for speaker in speakers):
            warnings.append(
                "Some speaker identities lack unambiguous textual evidence."
            )
        evidence_rejected = (
            first_binding.rejected + second_binding.rejected + semantic_rejections
        )
        evidence_review = (
            first_binding.review_required + second_binding.review_required
        )
        if evidence_rejected or evidence_review:
            warnings.append(
                f"Evidence checks excluded {evidence_rejected} candidates and marked {evidence_review} ambiguous."
            )
        unresolved_dates = sum(
            task.deadline_raw is not None and task.deadline_date is None
            for task in tasks
        )
        if unresolved_dates:
            warnings.append(
                f"{unresolved_dates} deadlines remain raw: event-based, ambiguous, unsupported, or missing time context."
            )
        return MeetingAnalysisResult(
            duration_ms=max(segment.end_ms for segment in segments),
            summary=summary,
            speakers=speakers,
            segments=SegmentTagger().tag(segments, tasks, problems),
            tasks=tasks,
            problems=problems,
            diagnostics=Diagnostics(
                status="text-analysis-complete",
                processing_time_ms=round((monotonic() - started_at) * 1000),
                llm_model=self._extractor.model,
                prompt_version=self._extractor.prompt_version,
                repair_attempts=sum(repairs.values()),
                stage_repair_attempts=repairs,
                verification_counts=counts,
                evidence_binding_counts={
                    "corrected": first_binding.corrected + second_binding.corrected,
                    "rejected": evidence_rejected,
                    "reviewRequired": (
                        first_binding.review_required
                        + second_binding.review_required
                    ),
                },
                invalid_deadline_raw_discarded=invalid_deadlines,
                stage_time_ms=stage_times,
                warnings=warnings,
            ),
        )
