import asyncio
import json
import unittest

from app.intelligence.fact_evidence import validate_fact_evidence
from app.intelligence.meeting_facts_extractor import MeetingFactsExtractor
from app.intelligence.speaker_resolver import SpeakerResolver
from app.intelligence.summary import SummaryGenerator
from app.intelligence.task_verifier import TaskVerifier
from app.pipeline import MeetingPipeline
from app.providers.structured_llm import StructuredLlm
from app.schemas import MeetingContext, Segment
from app.service_error import ServiceError


class ScriptedProvider:
    model = "local-fixture"

    def __init__(self, outputs):
        self.outputs = iter(outputs)
        self.calls = []

    async def generate_json(self, messages, schema):
        self.calls.append((messages.copy(), schema))
        value = next(self.outputs)
        if isinstance(value, Exception):
            raise value
        return (
            value if isinstance(value, str) else json.dumps(value, ensure_ascii=False)
        )


def segment(identifier, text, speaker="s1"):
    return Segment(id=identifier, speakerId=speaker, startMs=0, endMs=1000, text=text)


def task(identifier="t1", **updates):
    return {
        "id": identifier,
        "text": "Отправить отчёт",
        "assigneeName": "Марат",
        "assignerName": "Анна Петровна",
        "deadlineRaw": "до пятницы",
        "deadlineDate": None,
        "sourceSegmentIds": ["s1"],
        **updates,
    }


def verdict(identifier="t1", status="CONFIRMED", corrected=None):
    return {
        "candidateId": identifier,
        "evidenceAssessment": "Checked against original dialogue.",
        "status": status,
        "correctedTask": corrected,
    }


class IntelligenceTests(unittest.IsolatedAsyncioTestCase):
    def build(self, outputs):
        provider = ScriptedProvider(outputs)
        llm = StructuredLlm(provider)
        return MeetingPipeline(
            MeetingFactsExtractor(llm, 20000), TaskVerifier(llm), SummaryGenerator(llm)
        ), provider

    async def test_complete_pipeline(self):
        pipeline, provider = self.build(
            [
                {
                    "tasks": [task()],
                    "problems": [
                        {
                            "id": "p1",
                            "text": "Отчёт задержан",
                            "reportedBy": "Анна Петровна",
                            "sourceSegmentIds": ["s1"],
                        }
                    ],
                },
                {"verdicts": [verdict()]},
                {"taskIds": ["t1"], "problemIds": ["p1"]},
            ]
        )
        source = segment(
            "s1", "Анна Петровна: Отчёт задержан. Марат, отправьте отчёт до пятницы."
        )
        result = await pipeline.analyze_transcript(
            [source], MeetingContext(startedAt="2026-09-23T10:00:00+05:00")
        )
        self.assertEqual(str(result.tasks[0].deadline_date), "2026-09-25")
        self.assertEqual(result.tasks[0].assigner_name, "Анна Петровна")
        self.assertEqual(result.problems[0].reported_by, "Анна Петровна")
        self.assertEqual(result.segments[0].tags, ["TASK", "PROBLEM"])
        self.assertEqual(source.tags, [])
        self.assertIn("Марат", result.summary)
        self.assertEqual(len(provider.calls), 3)
        self.assertIn("durationMs", result.model_dump(by_alias=True))

    async def test_verifier_filters_and_corrects_before_tags_summary(self):
        candidates = [
            task("t1"),
            task("t2", text="Купить сервер"),
            task("t3", text="Сменить поставщика"),
        ]
        corrected = task("t1", deadlineRaw="до среды")
        pipeline, provider = self.build(
            [
                {"tasks": candidates, "problems": []},
                {
                    "verdicts": [
                        verdict("t1", "CORRECTED", corrected),
                        verdict("t2", "REJECTED"),
                        verdict("t3", "REVIEW_REQUIRED"),
                    ]
                },
                {"taskIds": ["t1"], "problemIds": []},
            ]
        )
        source = segment(
            "s1", "Анна Петровна: Марат, отчёт до пятницы. Уточняю: до среды."
        )
        result = await pipeline.analyze_transcript([source], MeetingContext())
        self.assertEqual([item.id for item in result.tasks], ["t1"])
        self.assertEqual(result.tasks[0].deadline_raw, "до среды")
        self.assertIsNone(result.tasks[0].deadline_date)
        self.assertEqual(
            result.diagnostics.verification_counts,
            {"CORRECTED": 1, "REJECTED": 1, "REVIEW_REQUIRED": 1},
        )
        payload = json.loads(provider.calls[-1][0][1]["content"])
        self.assertEqual(len(payload["tasks"]), 1)
        self.assertNotIn("Купить сервер", result.summary)

    async def test_no_fail_open_on_bad_verifier(self):
        pipeline, provider = self.build(
            [
                {"tasks": [task()], "problems": []},
                {"verdicts": []},
                {"verdicts": [verdict("invented")]},
            ]
        )
        with self.assertRaisesRegex(ServiceError, "task_verifier"):
            await pipeline.analyze_transcript(
                [segment("s1", "Анна Петровна: Марат, отчёт до пятницы.")],
                MeetingContext(),
            )
        self.assertEqual(len(provider.calls), 3)

    async def test_one_repair_and_no_transport_retry(self):
        pipeline, provider = self.build(["invalid", {"tasks": [], "problems": []}])
        result = await pipeline.analyze_transcript(
            [segment("s1", "Обсуждение завершено.")], MeetingContext()
        )
        self.assertTrue(result.summary)
        self.assertEqual(result.diagnostics.repair_attempts, 1)
        self.assertEqual(len(provider.calls), 2)
        pipeline, provider = self.build([ServiceError("LLM_TIMEOUT", "timeout")])
        with self.assertRaises(ServiceError):
            await pipeline.analyze_transcript(
                [segment("s1", "Текст")], MeetingContext()
            )
        self.assertEqual(len(provider.calls), 1)

    async def test_unknown_evidence_is_repaired_or_fails(self):
        bad = {"tasks": [task(sourceSegmentIds=["missing"])], "problems": []}
        pipeline, provider = self.build([bad, bad])
        with self.assertRaises(ServiceError):
            await pipeline.analyze_transcript(
                [segment("s1", "Текст")], MeetingContext()
            )
        self.assertEqual(len(provider.calls), 2)

    async def test_all_rejected_have_no_task_tags(self):
        pipeline, _ = self.build(
            [
                {"tasks": [task()], "problems": []},
                {"verdicts": [verdict(status="REJECTED")]},
            ]
        )
        result = await pipeline.analyze_transcript(
            [segment("s1", "Анна Петровна: Марат, может отчёт до пятницы?")],
            MeetingContext(),
        )
        self.assertEqual(result.tasks, [])
        self.assertEqual(result.segments[0].tags, [])
        self.assertTrue(result.summary)

    async def test_identical_correction_is_counted_as_confirmation(self):
        pipeline, _ = self.build(
            [
                {"tasks": [task()], "problems": []},
                {"verdicts": [verdict(status="CORRECTED", corrected=task())]},
                {"taskIds": ["t1"], "problemIds": []},
            ]
        )
        result = await pipeline.analyze_transcript(
            [segment("s1", "Анна Петровна: Марат, отчёт до пятницы.")], MeetingContext()
        )
        self.assertEqual(result.diagnostics.verification_counts, {"CONFIRMED": 1})

    async def test_non_temporal_deadline_and_paraphrased_problem_fail(self):
        for facts in (
            {"tasks": [task(deadlineRaw="отправьте отчёт")], "problems": []},
            {
                "tasks": [],
                "problems": [
                    {
                        "id": "p1",
                        "text": "Выручка упала на 68%",
                        "reportedBy": "Анна Петровна",
                        "sourceSegmentIds": ["s1"],
                    }
                ],
            },
        ):
            pipeline, _ = self.build([facts, facts])
            with self.assertRaises(ServiceError):
                await pipeline.analyze_transcript(
                    [
                        segment(
                            "s1",
                            "Анна Петровна: Марат, отправьте отчёт. Освоено 68% бюджета.",
                        )
                    ],
                    MeetingContext(),
                )

    async def test_summary_cannot_invent_facts(self):
        pipeline, _ = self.build(
            [
                {"tasks": [task()], "problems": []},
                {"verdicts": [verdict()]},
                {"taskIds": ["invented"], "problemIds": []},
                {"taskIds": ["t1"], "problemIds": []},
            ]
        )
        result = await pipeline.analyze_transcript(
            [segment("s1", "Анна Петровна: Марат, отчёт до пятницы.")], MeetingContext()
        )
        self.assertEqual(result.diagnostics.stage_repair_attempts["summary"], 1)

    async def test_busy_and_cancellation_release_lock(self):
        pipeline, _ = self.build([])
        entered, release = asyncio.Event(), asyncio.Event()

        async def waiting(*args):
            entered.set()
            await release.wait()

        pipeline._extractor.extract = waiting
        running = asyncio.create_task(
            pipeline.analyze_transcript([segment("s1", "Текст")], MeetingContext())
        )
        await entered.wait()
        with self.assertRaisesRegex(ServiceError, "already running"):
            await pipeline.analyze_transcript(
                [segment("s2", "Текст")], MeetingContext()
            )
        running.cancel()
        with self.assertRaises(asyncio.CancelledError):
            await running
        self.assertFalse(pipeline._lock.locked())

    async def test_oversize_before_inference(self):
        pipeline, provider = self.build([])
        with self.assertRaises(ServiceError) as error:
            await pipeline.analyze_transcript(
                [segment("s1", "Я" * 21000)], MeetingContext()
            )
        self.assertEqual(error.exception.code, "TRANSCRIPT_TOO_LARGE")
        self.assertEqual(provider.calls, [])

    def test_unknown_identity_and_conflicting_labels(self):
        segments = [
            segment("s1", "Я Марат, сделаю."),
            segment("s2", "Я Ерлан, проверю."),
        ]
        self.assertIsNone(SpeakerResolver().resolve(segments)[0].name)
        self.assertIsNone(
            SpeakerResolver()
            .resolve([segment("s1", "Марат, проверьте отчёт.")])[0]
            .name
        )

    def test_speaker_role_must_have_source_evidence(self):
        from app.intelligence.extracted_task import ExtractedTask

        segments = [segment("s1", "Марат, отчёт до пятницы.")]
        with self.assertRaises(ValueError):
            validate_fact_evidence(
                [ExtractedTask.model_validate(task())],
                [],
                segments,
                SpeakerResolver().resolve(segments),
            )
