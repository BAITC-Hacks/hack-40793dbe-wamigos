from app.schemas import Problem, Segment, SegmentTag, Task


class SegmentTagger:
    def tag(
        self, segments: list[Segment], tasks: list[Task], problems: list[Problem]
    ) -> list[Segment]:
        task_sources = {
            identifier for task in tasks for identifier in task.source_segment_ids
        }
        problem_sources = {
            identifier
            for problem in problems
            for identifier in problem.source_segment_ids
        }
        return [
            segment.model_copy(
                update={
                    "tags": [
                        tag
                        for tag, sources in (
                            (SegmentTag.TASK, task_sources),
                            (SegmentTag.PROBLEM, problem_sources),
                        )
                        if segment.id in sources
                    ]
                }
            )
            for segment in segments
        ]
