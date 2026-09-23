from collections import Counter

from app.intelligence.extracted_task import ExtractedTask
from app.intelligence.task_verification import TaskVerification
from app.providers.structured_llm import StructuredLlm
from app.schemas import Segment, Speaker


class TaskVerifier:
    def __init__(self, llm: StructuredLlm) -> None:
        self._llm = llm

    async def verify(
        self,
        candidates: list[ExtractedTask],
        segments: list[Segment],
        speakers: list[Speaker],
    ) -> tuple[list[ExtractedTask], dict[str, int], int]:
        if not candidates:
            return [], {}, 0
        by_id = {task.id: task for task in candidates}
        names = {speaker.id: speaker.name for speaker in speakers}

        def validate(result: TaskVerification) -> None:
            ids = [verdict.candidate_id for verdict in result.verdicts]
            if len(ids) != len(set(ids)) or set(ids) != set(by_id):
                raise ValueError(
                    "Return exactly one verdict for every candidate ID; do not invent or omit IDs"
                )

        result, repairs = await self._llm.generate(
            "task_verifier",
            {
                "transcript": "\n\n".join(
                    f"SEGMENT {segment.id}\n"
                    f"SPEAKER: {names.get(segment.speaker_id) or segment.speaker_id or 'UNKNOWN'}\n"
                    f"TEXT: {segment.text}"
                    for segment in segments
                ),
                "candidates": [
                    task.model_dump(mode="json", by_alias=True) for task in candidates
                ],
            },
            TaskVerification,
            validate,
        )
        return (
            self._accepted(result, by_id),
            dict(
                Counter(
                    "CONFIRMED"
                    if verdict.corrected_task == by_id[verdict.candidate_id]
                    else verdict.status
                    for verdict in result.verdicts
                )
            ),
            repairs,
        )

    def _accepted(
        self, result: TaskVerification, candidates: dict[str, ExtractedTask]
    ) -> list[ExtractedTask]:
        tasks = []
        for verdict in result.verdicts:
            if verdict.status == "CONFIRMED":
                tasks.append(candidates[verdict.candidate_id])
            elif verdict.corrected_task is not None:
                tasks.append(verdict.corrected_task)
        return tasks
