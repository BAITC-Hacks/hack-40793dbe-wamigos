from app.intelligence.summary_selection import SummarySelection
from app.providers.structured_llm import StructuredLlm
from app.schemas import Problem, Task


class SummaryGenerator:
    def __init__(self, llm: StructuredLlm) -> None:
        self._llm = llm

    async def generate(
        self, tasks: list[Task], problems: list[Problem]
    ) -> tuple[str, int]:
        if not tasks and not problems:
            return "Подтверждённых поручений и озвученных проблем не выявлено.", 0
        task_map = {task.id: task for task in tasks}
        problem_map = {problem.id: problem for problem in problems}

        def validate(selection: SummarySelection) -> None:
            for identifiers, known in (
                (selection.task_ids, task_map),
                (selection.problem_ids, problem_map),
            ):
                if len(identifiers) != len(set(identifiers)) or not set(
                    identifiers
                ) <= set(known):
                    raise ValueError(
                        "Summary may select only unique IDs of supplied verified facts"
                    )
                if known and not identifiers:
                    raise ValueError(
                        "Select at least one item from each nonempty fact collection"
                    )

        selected, repairs = await self._llm.generate(
            "summary",
            {
                "tasks": [
                    task.model_dump(mode="json", by_alias=True) for task in tasks
                ],
                "problems": [problem.model_dump(by_alias=True) for problem in problems],
            },
            SummarySelection,
            validate,
        )
        parts = []
        if problems:
            parts.append(
                "Озвученные проблемы: "
                + "; ".join(
                    problem_map[identifier].text.rstrip(".!?;")
                    for identifier in selected.problem_ids
                )
                + "."
            )
        if tasks:
            parts.append(
                f"Подтверждено поручений: {len(tasks)}. Основные: "
                + "; ".join(
                    self._task_text(task_map[identifier])
                    for identifier in selected.task_ids
                )
                + "."
            )
        return " ".join(parts), repairs

    def _task_text(self, task: Task) -> str:
        details = [task.text.rstrip(".!?;")]
        if task.assignee_name:
            details.append("ответственный: " + task.assignee_name)
        if task.deadline_raw:
            details.append("срок: " + task.deadline_raw)
        return " — ".join(details)
