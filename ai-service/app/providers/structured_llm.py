import json
from collections.abc import Callable
from pathlib import Path
from typing import TypeVar

from pydantic import BaseModel, ValidationError

from app.providers.llm_base import LlmProvider
from app.service_error import ServiceError

Output = TypeVar("Output", bound=BaseModel)


class StructuredLlm:
    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def generate(
        self,
        prompt_name: str,
        payload: dict,
        schema: type[Output],
        validate: Callable[[Output], None],
    ) -> tuple[Output, int]:
        prompt = (
            Path(__file__).resolve().parents[1] / "prompts" / f"{prompt_name}.txt"
        ).read_text(encoding="utf-8")
        messages = [
            {"role": "system", "content": prompt},
            {"role": "user", "content": json.dumps(payload, ensure_ascii=False)},
        ]
        for attempt in range(2):
            output = await self.provider.generate_json(
                messages, schema.model_json_schema()
            )
            try:
                result = schema.model_validate_json(output)
                validate(result)
                return result, attempt
            except ValueError as error:
                if attempt == 1:
                    break
                feedback = (
                    json.dumps(
                        error.errors(
                            include_input=False,
                            include_url=False,
                            include_context=False,
                        )
                    )
                    if isinstance(error, ValidationError)
                    else str(error)
                )
                messages.append({"role": "assistant", "content": output})
                messages.append(
                    {
                        "role": "user",
                        "content": "Regenerate the COMPLETE JSON from the original evidence, following the system rules. Preserve correct facts and valid existing source IDs. When adding a missing instruction source, KEEP the separate deadline/acceptance source too; multiple IDs are necessary. Fix ALL these validation errors: "
                        + feedback,
                    }
                )
        raise ServiceError(
            "LLM_INVALID_FACTS",
            f"Local model output for {prompt_name} failed validation after one repair attempt.",
        )
