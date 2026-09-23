import re
import unicodedata
from dataclasses import dataclass

from app.intelligence.meeting_facts import MeetingFacts
from app.schemas import Segment


@dataclass(frozen=True)
class EvidenceBindingStats:
    corrected: int = 0
    rejected: int = 0
    review_required: int = 0


class EvidenceBinder:
    def bind(
        self, facts: MeetingFacts, segments: list[Segment]
    ) -> tuple[MeetingFacts, EvidenceBindingStats]:
        tasks = []
        problems = []
        corrected = rejected = review_required = 0

        for candidate in facts.tasks:
            bound_ids, status = self._bind_quotes(candidate.evidence_quotes, segments)
            if status == "rejected":
                rejected += 1
                continue
            if status == "review-required":
                review_required += 1
                continue
            if bound_ids != candidate.source_segment_ids:
                corrected += 1
            tasks.append(candidate.model_copy(update={"source_segment_ids": bound_ids}))

        for candidate in facts.problems:
            bound_ids, status = self._bind_quotes(candidate.evidence_quotes, segments)
            if status == "rejected":
                rejected += 1
                continue
            if status == "review-required":
                review_required += 1
                continue
            if bound_ids != candidate.source_segment_ids:
                corrected += 1
            problems.append(candidate.model_copy(update={"source_segment_ids": bound_ids}))
        return (
            MeetingFacts(tasks=tasks, problems=problems),
            EvidenceBindingStats(
                corrected=corrected,
                rejected=rejected,
                review_required=review_required,
            ),
        )

    def _bind_quotes(
        self, quotes: list[str], segments: list[Segment]
    ) -> tuple[list[str], str]:
        bound_ids = []
        for quote in quotes:
            normalized_quote = self._normalize(quote)
            if len(normalized_quote) < 8:
                return [], "rejected"
            matches = [
                segment.id
                for segment in segments
                if normalized_quote in self._normalize(segment.text)
            ]
            if not matches:
                return [], "rejected"
            if len(matches) > 1:
                return [], "review-required"
            if matches[0] not in bound_ids:
                bound_ids.append(matches[0])
        return bound_ids, "confirmed"

    def _normalize(self, value: str) -> str:
        value = unicodedata.normalize("NFKC", value).casefold()
        return " ".join(re.sub(r"[^\wәғқңөұүһі]+", " ", value).split())
