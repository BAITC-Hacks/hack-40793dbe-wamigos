import argparse
import asyncio
import os

from app.config import get_settings
from app.intelligence.extracted_task import ExtractedTask
from app.intelligence.speaker_resolver import SpeakerResolver
from app.intelligence.task_verifier import TaskVerifier
from app.main import create_app
from app.providers.structured_llm import StructuredLlm
from app.schemas import MeetingContext, Segment

CASES = [
    (
        "KK",
        [
            "Дана Асқарқызы: Жабдық жеткізу кешігіп жатыр. Айгерім, жеткізушімен ертеңге дейін хабарласыңыз.",
            "Айгерім Нұрланқызы: Жақсы, хабарласамын.",
        ],
        "Айгерім",
        "2026-09-24",
        "Дана Асқарқызы",
    ),
    (
        "MIXED",
        [
            "Тимур Болатович: Сервер істемей тұр. Ерлан, проверь конфигурацию жұмаға дейін.",
            "Ерлан Нұрланұлы: Жақсы, тексеремін.",
        ],
        "Ерлан",
        "2026-09-25",
        "Тимур Болатович",
    ),
    (
        "CORRECTION",
        [
            "Анна Петровна: Марат, пришлите отчёт до пятницы.",
            "Марат Серикович: Хорошо, пришлю.",
            "Анна Петровна: Меняю срок отчёта: до 30 сентября.",
        ],
        "Марат",
        "2026-09-30",
        "Анна Петровна",
    ),
    (
        "NO_TASKS",
        [
            "Анна Петровна: Может, купим новый сервер?",
            "Марат Серикович: Нет, пока не покупаем.",
        ],
        None,
        None,
        None,
    ),
]


def segments_from(lines):
    return [
        Segment(
            id=f"s{index}",
            speakerId=f"speaker-{line.split(':')[0]}",
            startMs=index * 1000,
            endMs=(index + 1) * 1000,
            text=line,
        )
        for index, line in enumerate(lines)
    ]


async def evaluate(model):
    if model:
        os.environ["LOCAL_LLM_MODEL"] = model
    get_settings.cache_clear()
    application = create_app()
    passed = True
    context = MeetingContext(
        startedAt="2026-09-23T10:00:00+05:00", timeZone="Asia/Almaty"
    )
    async with application.router.lifespan_context(application):
        for label, lines, assignee, deadline, assigner in CASES:
            result = await application.state.pipeline.analyze_transcript(
                segments_from(lines), context
            )
            if assignee:
                ok = (
                    len(result.tasks) == 1
                    and result.tasks[0].assignee_name is not None
                    and result.tasks[0].assignee_name.startswith(assignee)
                    and str(result.tasks[0].deadline_date) == deadline
                    and result.tasks[0].assigner_name == assigner
                )
                if label in {"KK", "MIXED"}:
                    ok = (
                        ok
                        and bool(result.problems)
                        and result.problems[0].reported_by == assigner
                    )
            else:
                ok = not result.tasks
            if label == "KK":
                ok = ok and "жеткізуші" in result.tasks[0].text.casefold()
            if label == "CORRECTION":
                ok = ok and {"s0", "s2"} <= set(result.tasks[0].source_segment_ids)
            passed &= ok
            print(
                label,
                "PASS" if ok else "FAIL",
                result.model_dump_json(by_alias=True, exclude={"segments", "speakers"}),
                flush=True,
            )
        segments = segments_from(CASES[-1][1])
        candidate = ExtractedTask(
            id="candidate-1",
            text="Купить новый сервер",
            assigneeName=None,
            assignerName="Анна Петровна",
            deadlineRaw=None,
            sourceSegmentIds=["s0"],
        )
        tasks, counts, _ = await TaskVerifier(
            StructuredLlm(application.state.llm_provider)
        ).verify([candidate], segments, SpeakerResolver().resolve(segments))
        ok = not tasks and counts.get("REJECTED") == 1
        print("INDEPENDENT_REJECTION", "PASS" if ok else "FAIL", counts, flush=True)
        passed &= ok
    return passed


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--model")
    arguments = parser.parse_args()
    raise SystemExit(0 if asyncio.run(evaluate(arguments.model)) else 1)
