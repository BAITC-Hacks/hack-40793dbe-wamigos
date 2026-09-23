from app.intelligence.task_verdict import TaskVerdict
from app.schemas import ApiModel


class TaskVerification(ApiModel):
    verdicts: list[TaskVerdict]
