from pydantic import Field

from app.schemas import ApiModel


class SummarySelection(ApiModel):
    task_ids: list[str] = Field(max_length=5)
    problem_ids: list[str] = Field(max_length=3)
