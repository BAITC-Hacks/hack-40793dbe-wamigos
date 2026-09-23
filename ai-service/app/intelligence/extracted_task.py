from pydantic import Field

from app.schemas import Task


class ExtractedTask(Task):
    evidence_quotes: list[str] = Field(
        min_length=1,
        description="Exact short quotes copied from the transcript that support this task.",
    )
    deadline_raw: str | None = Field(
        description=(
            "Copy the exact deadline phrase from the source in its original language. "
            "Kazakh 'ертеңге дейін' and 'жұмаға дейін' are deadlines just like Russian "
            "'до завтра' and 'до пятницы'. Use null only when no deadline is stated."
        )
    )
    deadline_date: None = None
