from pydantic import model_validator

from app.intelligence.extracted_problem import ExtractedProblem
from app.intelligence.extracted_task import ExtractedTask
from app.schemas import ApiModel


class MeetingFacts(ApiModel):
    tasks: list[ExtractedTask]
    problems: list[ExtractedProblem]

    @model_validator(mode="after")
    def reject_repeated_actions(self) -> "MeetingFacts":
        keys = [
            (
                " ".join(task.text.casefold().split()),
                (task.assignee_name or "").casefold(),
            )
            for task in self.tasks
        ]
        if len(keys) != len(set(keys)):
            raise ValueError(
                "Repeated action for the same assignee: merge confirmations and deadline "
                "corrections into one task using the final deadline and all supporting sources. "
                "For genuinely distinct tasks, include their different scope in the text."
            )
        return self
