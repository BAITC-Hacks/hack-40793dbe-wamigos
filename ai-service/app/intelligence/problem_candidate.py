from pydantic import Field

from app.schemas import Problem


class ProblemCandidate(Problem):
    evidence_quotes: list[str] = Field(
        min_length=1,
        description="Exact short quotes copied from the transcript that support this problem.",
    )
