from typing import Literal

from pydantic import Field, model_validator

from app.intelligence.extracted_task import ExtractedTask
from app.schemas import ApiModel


class TaskVerdict(ApiModel):
    candidate_id: str
    evidence_assessment: str = Field(
        min_length=1,
        max_length=280,
        description="First assess the ORIGINAL dialogue: is this actually ordered/accepted, refused, cancelled, or only proposed? Cite the relevant wording and resolve later corrections before choosing status.",
    )
    status: Literal["CONFIRMED", "CORRECTED", "REJECTED", "REVIEW_REQUIRED"]
    corrected_task: ExtractedTask | None

    @model_validator(mode="after")
    def validate_correction(self) -> "TaskVerdict":
        if (self.status == "CORRECTED") != (self.corrected_task is not None):
            raise ValueError(
                "Only CORRECTED requires correctedTask; other statuses require null"
            )
        if (
            self.corrected_task is not None
            and self.corrected_task.id != self.candidate_id
        ):
            raise ValueError("A correction must preserve the candidate ID")
        return self
