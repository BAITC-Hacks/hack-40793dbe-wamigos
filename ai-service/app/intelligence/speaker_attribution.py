from app.intelligence.extracted_task import ExtractedTask
from app.intelligence.problem_candidate import ProblemCandidate
from app.schemas import Segment, Speaker


class SpeakerAttribution:
    def apply(
        self,
        tasks: list[ExtractedTask],
        problems: list[ProblemCandidate],
        segments: list[Segment],
        speakers: list[Speaker],
    ) -> tuple[list[ExtractedTask], list[ProblemCandidate]]:
        by_segment = {segment.id: segment for segment in segments}
        names = {speaker.id: speaker.name for speaker in speakers}
        attributed_tasks = [
            task.model_copy(
                update={
                    "assigner_name": self._resolve_role(
                        task.source_segment_ids,
                        task.assigner_name,
                        task.assignee_name,
                        by_segment,
                        names,
                    )
                }
            )
            for task in tasks
        ]
        attributed_problems = [
            problem.model_copy(
                update={
                    "reported_by": self._resolve_role(
                        problem.source_segment_ids,
                        problem.reported_by,
                        None,
                        by_segment,
                        names,
                    )
                }
            )
            for problem in problems
        ]
        return attributed_tasks, attributed_problems

    def _resolve_role(
        self,
        source_ids: list[str],
        proposed_name: str | None,
        excluded_name: str | None,
        segments: dict[str, Segment],
        names: dict[str, str | None],
    ) -> str | None:
        source_names = {
            name
            for identifier in source_ids
            if (segment := segments.get(identifier)) is not None
            if (name := names.get(segment.speaker_id)) is not None
            if name != excluded_name
        }
        if proposed_name in source_names:
            return proposed_name
        if len(source_names) == 1:
            return next(iter(source_names))
        return None
