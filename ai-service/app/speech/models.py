from pydantic import Field

from app.schemas import ApiModel, Segment


class AsrWord(ApiModel):
    text: str
    start_ms: int = Field(ge=0)
    end_ms: int = Field(ge=0)
    probability: float | None = None


class AsrSegment(ApiModel):
    start_ms: int = Field(ge=0)
    end_ms: int = Field(ge=0)
    text: str
    language: str | None = None
    words: list[AsrWord] = Field(default_factory=list)


class SpeakerTurn(ApiModel):
    speaker_id: str
    start_ms: int = Field(ge=0)
    end_ms: int = Field(ge=0)


class SpeechTranscript(ApiModel):
    duration_ms: int = Field(ge=0)
    segments: list[Segment]
    detected_language: str | None = None
    warnings: list[str] = Field(default_factory=list)
