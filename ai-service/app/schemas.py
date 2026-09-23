from datetime import date, datetime
from enum import Enum
from typing import Annotated, Literal

from pydantic import AfterValidator, BaseModel, ConfigDict, Field, model_validator
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
    started_at: datetime | None = None
    time_zone: str | None = Field(default=None, min_length=1, max_length=100)


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
        if self.end_ms < self.start_ms:
            raise ValueError("end_ms must be greater than or equal to start_ms")
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
    repair_attempts: int = Field(default=0, ge=0, le=1)
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
    summary: str
    speakers: list[Speaker]
    segments: TranscriptSegments
    tasks: list[Task]
    problems: list[Problem]
    diagnostics: Diagnostics

    @model_validator(mode="after")
    def validate_source_segment_ids(self) -> "MeetingAnalysisResult":
        validate_fact_sources(self.tasks, self.problems, self.segments)
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
