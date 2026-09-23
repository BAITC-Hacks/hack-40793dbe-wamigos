import json
from pathlib import Path

from pydantic import ValidationError

from app.intelligence.meeting_facts import MeetingFacts
from app.providers.llm_base import LlmProvider
from app.schemas import MeetingContext, Segment, validate_fact_sources
from app.service_error import ServiceError


class MeetingFactsExtractor:
    prompt_version = "meeting-facts-v1"

    def __init__(self, provider: LlmProvider, max_input_chars: int) -> None:
        self._provider = provider
        self._max_input_chars = max_input_chars
        self._prompt = (
            Path(__file__).resolve().parents[1] / "prompts" / "meeting_facts.txt"
        ).read_text(encoding="utf-8")

    @property
    def model(self) -> str | None:
        return self._provider.model

    async def extract(
        self, segments: list[Segment], context: MeetingContext
    ) -> tuple[MeetingFacts, int]:
        content = json.dumps(
            {
                "context": context.model_dump(mode="json", by_alias=True),
                "segments": [
                    segment.model_dump(mode="json", by_alias=True, exclude={"tags"})
                    for segment in segments
                ],
            },
            ensure_ascii=False,
        )
        if len(content) > self._max_input_chars:
            raise ServiceError(
                "TRANSCRIPT_TOO_LARGE", "Transcript exceeds LOCAL_LLM_MAX_INPUT_CHARS."
            )

        messages = [
            {"role": "system", "content": self._prompt},
            {"role": "user", "content": content},
        ]
        for attempt in range(2):
            output = await self._provider.generate_json(
                messages, MeetingFacts.model_json_schema()
            )
            try:
                facts = MeetingFacts.model_validate_json(output)
                validate_fact_sources(facts.tasks, facts.problems, segments)
                return facts, attempt
            except ValueError as error:
                if attempt == 1:
                    break
                feedback = self._validation_feedback(error)
                messages.append(
                    {
                        "role": "user",
                        "content": (
                            "The previous output was invalid. Regenerate the complete JSON from "
                            "the original transcript; follow the schema and use only existing segment IDs. "
                            "Keep deadlineDate, assignerName and reportedBy null. "
                            "Validation errors: " + feedback
                        ),
                    }
                )
        raise ServiceError(
            "LLM_INVALID_FACTS",
            "Local model output failed validation after one repair attempt.",
        )

    def _validation_feedback(self, error: ValueError) -> str:
        if isinstance(error, ValidationError):
            return json.dumps(
                [
                    {
                        "location": item["loc"],
                        "type": item["type"],
                        "message": item["msg"],
                    }
                    for item in error.errors(include_input=False, include_url=False)
                ]
            )
        return str(error)
