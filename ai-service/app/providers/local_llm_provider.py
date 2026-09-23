import asyncio

import httpx

from app.config import Settings
from app.schemas import LlmHealth
from app.service_error import ServiceError


class LocalLlmProvider:
    def __init__(self, client: httpx.AsyncClient, settings: Settings) -> None:
        self._client = client
        self._settings = settings

    @property
    def model(self) -> str | None:
        return (self._settings.local_llm_model or "").strip() or None

    async def generate_json(self, messages: list[dict[str, str]], schema: dict) -> str:
        if self.model is None:
            raise ServiceError(
                "LLM_NOT_CONFIGURED", "Set LOCAL_LLM_MODEL to a local model."
            )
        payload = {
            "model": self.model,
            "messages": messages,
            "stream": False,
            "temperature": 0,
            "max_tokens": self._settings.local_llm_max_tokens,
            "response_format": {
                "type": "json_schema",
                "json_schema": {
                    "name": "meeting_facts",
                    "strict": True,
                    "schema": schema,
                },
            },
        }
        try:
            async with asyncio.timeout(self._settings.local_llm_timeout_seconds):
                response = await self._client.post("chat/completions", json=payload)
        except (TimeoutError, httpx.TimeoutException) as error:
            raise ServiceError(
                "LLM_TIMEOUT", "Local model exceeded the response timeout."
            ) from error
        except httpx.RequestError as error:
            raise ServiceError(
                "LLM_UNAVAILABLE", "Cannot connect to the local model."
            ) from error

        if response.status_code == 404:
            raise ServiceError(
                "LLM_MODEL_NOT_FOUND", "Local model or endpoint was not found."
            )
        if response.status_code == 429 or response.status_code >= 500:
            raise ServiceError("LLM_UNAVAILABLE", "Local model is unavailable or busy.")
        if not response.is_success:
            raise ServiceError(
                "LLM_REQUEST_REJECTED", "Local runtime rejected the inference request."
            )

        try:
            choice = response.json()["choices"][0]
            message = choice["message"]
            if choice.get("finish_reason") != "stop" or message.get("refusal"):
                raise ValueError("Incomplete or refused response")
            content = message["content"]
            if not isinstance(content, str) or not content.strip():
                raise ValueError("Empty model response")
            return content
        except (ValueError, KeyError, IndexError, TypeError, AttributeError) as error:
            raise ServiceError(
                "LLM_INVALID_RESPONSE",
                "Local model returned an incomplete or invalid response.",
            ) from error

    async def check_health(self) -> LlmHealth:
        if self.model is None:
            return LlmHealth(status="not-configured", model=None)
        try:
            async with asyncio.timeout(self._settings.local_llm_health_timeout_seconds):
                response = await self._client.get("models")
                response.raise_for_status()
                model_ids = {entry["id"] for entry in (response.json()["data"] or [])}
            status = "ready" if self.model in model_ids else "model-not-found"
            return LlmHealth(status=status, model=self.model)
        except (TimeoutError, httpx.HTTPError, ValueError, KeyError, TypeError):
            return LlmHealth(status="unavailable", model=self.model)
