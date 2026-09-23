from typing import Protocol


class LlmProvider(Protocol):
    @property
    def model(self) -> str | None: ...

    async def generate_json(
        self, messages: list[dict[str, str]], schema: dict
    ) -> str: ...
