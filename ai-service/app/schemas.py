from datetime import date, datetime
from enum import Enum
from typing import Annotated, Literal
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from pydantic import (
    AfterValidator,
    AwareDatetime,
    BaseModel,
    ConfigDict,
    Field,
    field_validator,
    model_validator,
)
from pydantic.alias_generators import to_camel


class ApiModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        extra="forbid",
        populate_by_name=True,
        str_strip_whitespace=True,
    )


class SegmentTag(str, Enum):
    TASK = "TASK"
    PROBLEM = "PROBLEM"


class MeetingContext(ApiModel):
    started_at: AwareDatetime | None = None
    time_zone: str | None = Field(default=None, min_length=1, max_length=100)

    @field_validator("started_at", mode="before")
    @classmethod
    def validate_started_at(cls, value: str | datetime | None) -> datetime | None:
        if isinstance(value, str):
            return datetime.fromisoformat(value)
        if value is not None and not isinstance(value, datetime):
            raise ValueError("startedAt must be an ISO-8601 datetime with offset")
        return value

    @field_validator("time_zone")
    @classmethod
    def validate_timezone(cls, value: str | None) -> str | None:
        if value is not None:
            try:
                ZoneInfo(value)
            except (ZoneInfoNotFoundError, ValueError) as error:
                raise ValueError("timeZone must be an IANA timezone") from error
        return value


class Speaker(ApiModel):
    id: str = Field(min_length=1, max_length=100)
    name: str | None = Field(default=None, max_length=200)


class Segment(ApiModel):
    id: str = Field(min_length=1, max_length=100)
    speaker_id: str | None = Field(default=None, min_length=1, max_length=100)
    start_ms: int = Field(ge=0)
    end_ms: int = Field(ge=0)
    text: str = Field(min_length=1)
    tags: list[SegmentTag] = Field(default_factory=list)

    @model_validator(mode="after")
    def validate_time_range(self) -> "Segment":
        if self.end_ms <= self.start_ms:
            raise ValueError("end_ms must be greater than start_ms")
        return self


def validate_segments(segments: list[Segment]) -> list[Segment]:
    if not segments:
        raise ValueError("segments must not be empty")
    segment_ids = [segment.id for segment in segments]
    if len(segment_ids) != len(set(segment_ids)):
        raise ValueError("segment ids must be unique")
    return segments


TranscriptSegments = Annotated[
    list[Segment], Field(min_length=1), AfterValidator(validate_segments)
]


class Task(ApiModel):
    id: str = Field(min_length=1, max_length=100)
    text: str = Field(min_length=1)
    assignee_name: str | None = Field(default=None, max_length=200)
    assigner_name: str | None = Field(default=None, max_length=200)
    deadline_raw: str | None = None
    deadline_date: date | None = None
    source_segment_ids: list[str] = Field(min_length=1)


class Problem(ApiModel):
    id: str = Field(min_length=1, max_length=100)
    text: str = Field(min_length=1)
    reported_by: str | None = Field(default=None, max_length=200)
    source_segment_ids: list[str] = Field(min_length=1)


class Diagnostics(ApiModel):
    status: str = Field(min_length=1, max_length=100)
    processing_time_ms: int = Field(default=0, ge=0)
    llm_model: str | None = None
    prompt_version: str | None = None
    repair_attempts: int = Field(default=0, ge=0)
    stage_repair_attempts: dict[str, int] = Field(default_factory=dict)
    verification_counts: dict[str, int] = Field(default_factory=dict)
    evidence_binding_counts: dict[str, int] = Field(default_factory=dict)
    invalid_deadline_raw_discarded: int = Field(default=0, ge=0)
    stage_time_ms: dict[str, int] = Field(default_factory=dict)
    warnings: list[str] = Field(default_factory=list)


class AnalyzeTranscriptRequest(ApiModel):
    segments: TranscriptSegments
    context: MeetingContext = Field(default_factory=MeetingContext)


def validate_fact_sources(
    tasks: list[Task], problems: list[Problem], segments: list[Segment]
) -> None:
    known_segment_ids = {segment.id for segment in segments}
    speaker_ids = {
        segment.speaker_id.casefold()
        for segment in segments
        if segment.speaker_id is not None
    }
    for items in (tasks, problems):
        ids = [item.id for item in items]
        if len(ids) != len(set(ids)):
            raise ValueError("fact ids must be unique within tasks and within problems")
        for item in items:
            unknown_ids = set(item.source_segment_ids) - known_segment_ids
            if unknown_ids:
                raise ValueError(
                    "unknown source segment ids: " + ", ".join(sorted(unknown_ids))
                )
    names = [
        name for task in tasks for name in (task.assignee_name, task.assigner_name)
    ]
    names.extend(problem.reported_by for problem in problems)
    if any(name is not None and name.casefold() in speaker_ids for name in names):
        raise ValueError("Speaker IDs are not names; use null for unknown names")


class MeetingAnalysisResult(ApiModel):
    duration_ms: int = Field(ge=0)
    summary: str = Field(min_length=1)
    speakers: list[Speaker]
    segments: TranscriptSegments
    tasks: list[Task]
    problems: list[Problem]
    diagnostics: Diagnostics

    @model_validator(mode="after")
    def validate_source_segment_ids(self) -> "MeetingAnalysisResult":
        validate_fact_sources(self.tasks, self.problems, self.segments)
        speaker_ids = [speaker.id for speaker in self.speakers]
        if len(speaker_ids) != len(set(speaker_ids)):
            raise ValueError("speaker ids must be unique")
        if any(
            segment.speaker_id is not None and segment.speaker_id not in speaker_ids
            for segment in self.segments
        ):
            raise ValueError("unknown speaker id")
        if self.segments != sorted(
            self.segments, key=lambda segment: (segment.start_ms, segment.end_ms)
        ):
            raise ValueError("segments must be sorted by time")
        if self.duration_ms < max(segment.end_ms for segment in self.segments):
            raise ValueError("durationMs must cover all segments")
        if any(
            task.deadline_date is not None and task.deadline_raw is None
            for task in self.tasks
        ):
            raise ValueError("deadlineDate requires deadlineRaw")
        return self


class LlmHealth(ApiModel):
    status: Literal["ready", "not-configured", "unavailable", "model-not-found"]
    model: str | None


class HealthResponse(ApiModel):
    service: str
    version: str
    status: str
    dev_endpoints_enabled: bool
    intelligence_ready: bool
    llm_provider: LlmHealth


class ErrorResponse(ApiModel):
    code: str
    message: str
