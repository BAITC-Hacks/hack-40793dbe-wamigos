import re

from app.schemas import Segment, Speaker


class SpeakerResolver:
    _name_token = r"[А-ЯӘҒҚҢӨҰҮҺІЁA-Z][а-яәғқңөұүһіёa-z'-]+"
    _address_pattern = re.compile(
        rf"(?P<name>{_name_token}(?:\s+{_name_token})?)"
        r"\s*[,!.]?\s*(?i:вам слово|сізге сөз|сөз сізде|"
        r"прошу вас|доложите|расскажите|баяндаңыз|айтыңыз)"
    )

    def resolve(self, segments: list[Segment]) -> list[Speaker]:
        names: dict[str, set[str]] = {}
        for segment in segments:
            if segment.speaker_id is None:
                continue
            candidates = names.setdefault(segment.speaker_id, set())
            match = re.match(
                r"^([А-ЯӘҒҚҢӨҰҮҺІA-Z][\w'-]+(?: [А-ЯӘҒҚҢӨҰҮҺІA-Z][\w'-]+){1,2}):\s",
                segment.text,
            )
            if match is None:
                match = re.match(
                    r"^(?:Я|Менің атым|Меня зовут) ([А-ЯӘҒҚҢӨҰҮҺІA-Z][\w'-]+(?: [А-ЯӘҒҚҢӨҰҮҺІA-Z][\w'-]+){0,2})(?=[,.!]|$)",
                    segment.text,
                )
            if match is not None:
                candidates.add(match.group(1))
        self._resolve_addressed_speakers(segments, names)
        return [
            Speaker(
                id=identifier, name=next(iter(values)) if len(values) == 1 else None
            )
            for identifier, values in sorted(names.items())
        ]

    def _resolve_addressed_speakers(
        self, segments: list[Segment], names: dict[str, set[str]]
    ) -> None:
        for current, following in zip(segments, segments[1:]):
            if (
                current.speaker_id is None
                or following.speaker_id is None
                or current.speaker_id == following.speaker_id
            ):
                continue
            match = self._address_pattern.search(current.text)
            if match is not None:
                names.setdefault(following.speaker_id, set()).add(match.group("name"))
