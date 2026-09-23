import argparse
import asyncio
import json
import os
import re
from pathlib import Path

from app.config import get_settings
from app.main import create_app
from app.schemas import AnalyzeTranscriptRequest
from app.service_error import ServiceError

EXPECTATIONS = {
    1: [
        ("Гульмира Сериковна", r"стратеги", "2026-10-15"),
        ("Айнур Каировна", r"катализатор|график постав", "2026-09-26"),
        ("Тимур Болатович", r"финанс", "2026-09-30"),
        ("юридический департамент", r"штраф", "2026-09-30"),
        ("Гульмира Сериковна", r"сводный отч[её]т", "2026-10-20"),
        ("Гульмира Сериковна", r"подрядчик", "2026-09-25"),
        ("Нурлан Сагатович", r"аудит", "2026-10-15"),
        ("Нурлан Сагатович", r"инструктаж", None),
        ("Тимур Болатович", r"инвестплан|бюджет", None),
        ("Айнур Каировна", r"юрист|юридическ|претензи", "2026-09-23"),
    ],
    2: [
        ("Ерлан", r"претензи", "2026-09-27"),
        ("Ботагоз Нурлановна", r"альтернатив|второго поставщика", "2026-10-07"),
        ("Жандос Талгатович", r"совещание.*подрядчик", None),
        ("Жандос Талгатович", r"справк", None),
        ("Ерболат Мухтарович", r"смет", "2026-09-30"),
        ("Салтанат Ерболовна", r"уведомлен|шаблон", None),
    ],
}
REPORTERS = {
    1: {"Гульмира Сериковна", "Тимур Болатович", "Нурлан Сагатович"},
    2: {
        "Ботагоз Нурлановна",
        "Жандос Талгатович",
        "Ерболат Мухтарович",
        "Салтанат Ерболовна",
    },
}


async def evaluate(model: str | None, numbers: list[int]) -> bool:
    if model:
        os.environ["LOCAL_LLM_MODEL"] = model
    get_settings.cache_clear()
    application = create_app()
    passed = True
    async with application.router.lifespan_context(application):
        for number in numbers:
            data = json.loads(
                (Path(__file__).parent / "data" / f"protocol{number}.json").read_text()
            )
            request = AnalyzeTranscriptRequest.model_validate(data["request"])
            print(
                f"START Protocol {number}: {len(request.segments)} segments", flush=True
            )
            try:
                result = await application.state.pipeline.analyze_transcript(
                    request.segments, request.context
                )
            except (ServiceError, ValueError) as error:
                print(
                    json.dumps(
                        {"protocol": number, "error": str(error)}, ensure_ascii=False
                    ),
                    flush=True,
                )
                passed = False
                continue
            missing = []
            for assignee, pattern, deadline in EXPECTATIONS[number]:
                if not any(
                    (task.assignee_name or "").casefold() == assignee.casefold()
                    and re.search(pattern, task.text, re.IGNORECASE)
                    and (task.deadline_date.isoformat() if task.deadline_date else None)
                    == deadline
                    for task in result.tasks
                ):
                    missing.append(
                        {
                            "assignee": assignee,
                            "actionPattern": pattern,
                            "deadline": deadline,
                        }
                    )
            reporter_missing = sorted(
                REPORTERS[number] - {problem.reported_by for problem in result.problems}
            )
            chairman = "Асхат Ерланович" if number == 1 else "Данияр Серикович"
            wrong_assigners = [
                task.id for task in result.tasks if task.assigner_name != chairman
            ]
            absent_deadline_violation = number == 2 and any(
                task.assignee_name == "Салтанат Ерболовна"
                and task.deadline_raw is not None
                for task in result.tasks
            )
            ok = not (
                missing
                or reporter_missing
                or wrong_assigners
                or absent_deadline_violation
            )
            passed &= ok
            print(
                json.dumps(
                    {
                        "protocol": number,
                        "passed": ok,
                        "goldGroupsCovered": len(EXPECTATIONS[number]) - len(missing),
                        "goldGroups": len(EXPECTATIONS[number]),
                        "missing": missing,
                        "missingReporters": reporter_missing,
                        "wrongAssigners": wrong_assigners,
                        "absentDeadlineViolation": absent_deadline_violation,
                        "result": result.model_dump(
                            mode="json", by_alias=True, exclude={"segments"}
                        ),
                    },
                    ensure_ascii=False,
                    indent=2,
                ),
                flush=True,
            )
    return passed


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Full local-model protocol acceptance, no answer tables in model inputs."
    )
    parser.add_argument("--model")
    parser.add_argument("--protocol", type=int, choices=(1, 2), action="append")
    arguments = parser.parse_args()
    raise SystemExit(
        0 if asyncio.run(evaluate(arguments.model, arguments.protocol or [1, 2])) else 1
    )
