import unittest
from unittest.mock import AsyncMock, patch

import httpx
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.config import Settings
from app.main import create_app
from app.providers.local_llm_provider import LocalLlmProvider
from app.schemas import (
    AnalyzeTranscriptRequest,
    LlmHealth,
    MeetingAnalysisResult,
)
from app.service_error import ServiceError


class ContractTests(unittest.TestCase):
    def test_invalid_segments(self):
        cases = [
            [],
            [{"id": "s1", "startMs": 0, "endMs": 0, "text": "Речь"}],
            [{"id": "s1", "startMs": 0, "endMs": 1, "text": " "}],
            [{"id": "s1", "startMs": -1, "endMs": 1, "text": "Речь"}],
            [{"id": "s1", "startMs": 0, "endMs": 1, "text": "Речь"}] * 2,
        ]
        for segments in cases:
            with self.subTest(segments=segments), self.assertRaises(ValidationError):
                AnalyzeTranscriptRequest(segments=segments)

    def test_result_referential_invariants(self):
        valid = {
            "durationMs": 2,
            "summary": "Итог",
            "speakers": [{"id": "speaker-1", "name": None}],
            "segments": [
                {
                    "id": "s1",
                    "speakerId": "speaker-1",
                    "startMs": 0,
                    "endMs": 2,
                    "text": "Речь",
                }
            ],
            "tasks": [],
            "problems": [],
            "diagnostics": {"status": "complete"},
        }
        MeetingAnalysisResult.model_validate(valid)
        for changes in (
            {"speakers": []},
            {"speakers": valid["speakers"] * 2},
            {"durationMs": 1},
            {"summary": ""},
            {
                "segments": [
                    {"id": "s2", "startMs": 3, "endMs": 4, "text": "Позже"},
                    *valid["segments"],
                ],
                "durationMs": 4,
            },
        ):
            with self.subTest(changes=changes), self.assertRaises(ValidationError):
                MeetingAnalysisResult.model_validate({**valid, **changes})

    def test_disabled_dev_endpoint(self):
        settings = Settings(_env_file=None, ENABLE_DEV_ENDPOINTS=False)
        with (
            patch("app.main.get_settings", return_value=settings),
            TestClient(create_app()) as client,
        ):
            self.assertEqual(
                client.post("/dev/analyze-transcript", json={}).status_code, 404
            )
            self.assertNotIn(
                "/dev/analyze-transcript", client.get("/openapi.json").json()["paths"]
            )

    def test_transport_errors_and_health(self):
        settings = Settings(_env_file=None)
        with (
            patch("app.main.get_settings", return_value=settings),
            TestClient(create_app()) as client,
        ):
            application = client.app
            application.state.llm_provider.check_health = AsyncMock(
                return_value=LlmHealth(status="unavailable", model=None)
            )
            self.assertEqual(client.get("/health").status_code, 503)
            for code, status in (
                ("LLM_TIMEOUT", 504),
                ("ANALYSIS_BUSY", 503),
                ("LLM_INVALID_FACTS", 502),
                ("TRANSCRIPT_TOO_LARGE", 413),
            ):
                application.state.pipeline.analyze_transcript = AsyncMock(
                    side_effect=ServiceError(code, "Safe message")
                )
                response = client.post(
                    "/dev/analyze-transcript",
                    json={
                        "segments": [
                            {"id": "s1", "startMs": 0, "endMs": 1, "text": "Речь"}
                        ]
                    },
                )
                self.assertEqual(response.status_code, status)
                self.assertEqual(
                    response.json(), {"code": code, "message": "Safe message"}
                )
                if code == "ANALYSIS_BUSY":
                    self.assertEqual(response.headers["Retry-After"], "5")
            response = client.post(
                "/dev/analyze-transcript",
                json={
                    "context": {"startedAt": "2026-09-23T12:00:00"},
                    "segments": [
                        {"id": "s1", "startMs": 0, "endMs": 1, "text": "Речь"}
                    ],
                },
            )
            self.assertEqual(response.status_code, 422)


class ProviderTests(unittest.IsolatedAsyncioTestCase):
    async def test_invalid_completion_and_error_mapping(self):
        settings = Settings(_env_file=None, LOCAL_LLM_MODEL="local-model")
        for status, body, code in (
            (
                200,
                {
                    "choices": [
                        {"finish_reason": "length", "message": {"content": "{}"}}
                    ]
                },
                "LLM_INVALID_RESPONSE",
            ),
            (200, {"choices": []}, "LLM_INVALID_RESPONSE"),
            (404, {"error": "private upstream data"}, "LLM_MODEL_NOT_FOUND"),
            (429, {}, "LLM_UNAVAILABLE"),
            (500, {}, "LLM_UNAVAILABLE"),
            (400, {}, "LLM_REQUEST_REJECTED"),
        ):
            with self.subTest(status=status, body=body):
                async with httpx.AsyncClient(
                    base_url="http://localhost/v1/",
                    transport=httpx.MockTransport(
                        lambda request, status=status, body=body: httpx.Response(
                            status, json=body
                        )
                    ),
                ) as client:
                    with self.assertRaises(ServiceError) as error:
                        await LocalLlmProvider(client, settings).generate_json([], {})
                    self.assertEqual(error.exception.code, code)
                    self.assertNotIn("private upstream", error.exception.message)

    async def test_health_and_request_schema(self):
        settings = Settings(_env_file=None, LOCAL_LLM_MODEL="local-model")

        def respond(request):
            if request.url.path.endswith("/models"):
                return httpx.Response(200, json={"data": [{"id": "local-model"}]})
            return httpx.Response(
                200,
                json={
                    "choices": [{"finish_reason": "stop", "message": {"content": "{}"}}]
                },
            )

        async with httpx.AsyncClient(
            base_url="http://localhost/v1/", transport=httpx.MockTransport(respond)
        ) as client:
            provider = LocalLlmProvider(client, settings)
            self.assertEqual((await provider.check_health()).status, "ready")
            self.assertEqual(await provider.generate_json([], {}), "{}")
