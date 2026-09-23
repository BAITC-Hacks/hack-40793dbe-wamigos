from app.schemas import Segment
from app.speech.models import AsrSegment, AsrWord, SpeakerTurn


class TranscriptBuilder:
    def __init__(self, max_same_speaker_gap_ms: int = 1500) -> None:
        self._max_gap = max_same_speaker_gap_ms

    def build(
        self, asr_segments: list[AsrSegment], speaker_turns: list[SpeakerTurn]
    ) -> list[Segment]:
        words = [word for segment in asr_segments for word in segment.words]
        if words:
            rows = self._group_words(words, speaker_turns)
        else:
            rows = [
                (
                    self._speaker_for_range(
                        segment.start_ms, segment.end_ms, speaker_turns
                    ),
                    segment.start_ms,
                    segment.end_ms,
                    segment.text,
                )
                for segment in asr_segments
                if segment.text.strip()
            ]
        return [
            Segment(
                id=f"seg-{index}",
                speaker_id=speaker,
                start_ms=start,
                end_ms=max(start + 1, end),
                text=text.strip(),
            )
            for index, (speaker, start, end, text) in enumerate(rows, 1)
            if text.strip()
        ]

    def _group_words(
        self, words: list[AsrWord], turns: list[SpeakerTurn]
    ) -> list[tuple[str | None, int, int, str]]:
        groups: list[tuple[str | None, int, int, list[str]]] = []
        for word in words:
            speaker = self._speaker_for_range(word.start_ms, word.end_ms, turns)
            if (
                groups
                and groups[-1][0] == speaker
                and word.start_ms - groups[-1][2] <= self._max_gap
            ):
                previous = groups[-1]
                groups[-1] = (
                    previous[0],
                    previous[1],
                    word.end_ms,
                    [*previous[3], word.text],
                )
            else:
                groups.append((speaker, word.start_ms, word.end_ms, [word.text]))
        return [
            (speaker, start, end, self._join_words(texts))
            for speaker, start, end, texts in groups
        ]

    def _speaker_for_range(
        self, start_ms: int, end_ms: int, turns: list[SpeakerTurn]
    ) -> str | None:
        overlaps = [
            (max(0, min(end_ms, turn.end_ms) - max(start_ms, turn.start_ms)), turn)
            for turn in turns
        ]
        overlapping = [item for item in overlaps if item[0] > 0]
        if overlapping:
            return max(overlapping, key=lambda item: item[0])[1].speaker_id
        if not turns:
            return None
        midpoint = (start_ms + end_ms) // 2
        return min(
            turns,
            key=lambda turn: min(
                abs(midpoint - turn.start_ms), abs(midpoint - turn.end_ms)
            ),
        ).speaker_id

    def _join_words(self, words: list[str]) -> str:
        joined = "".join(words).strip()
        if " " not in joined and len(words) > 1:
            joined = " ".join(word.strip() for word in words)
        return joined
