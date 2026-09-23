import json

from app.intelligence.meeting_facts import MeetingFacts
from app.providers.structured_llm import StructuredLlm
from app.schemas import MeetingContext, Segment, Speaker
from app.service_error import ServiceError


class MeetingFactsExtractor:
    prompt_version = "meeting-facts-v2"

    def __init__(self, llm: StructuredLlm, max_input_chars: int) -> None:
        self._llm = llm
        self._max_input_chars = max_input_chars

    @property
    def model(self) -> str | None:
        return self._llm.provider.model

    async def extract(
        self, segments: list[Segment], context: MeetingContext, speakers: list[Speaker]
    ) -> tuple[MeetingFacts, int]:
        names = {speaker.id: speaker.name for speaker in speakers}
        payload = {
            "context": context.model_dump(
                mode="json", by_alias=True, exclude_none=True
            ),
            "transcript": "\n\n".join(
                f"SEGMENT {segment.id}\n"
                f"SPEAKER: {names.get(segment.speaker_id) or segment.speaker_id or 'UNKNOWN'}\n"
                f"TEXT: {segment.text}"
                for segment in segments
            ),
        }
        if len(json.dumps(payload, ensure_ascii=False)) > self._max_input_chars:
            raise ServiceError(
                "TRANSCRIPT_TOO_LARGE", "Transcript exceeds LOCAL_LLM_MAX_INPUT_CHARS."
            )

        return await self._llm.generate(
            "meeting_facts", payload, MeetingFacts, lambda _: None
        )
