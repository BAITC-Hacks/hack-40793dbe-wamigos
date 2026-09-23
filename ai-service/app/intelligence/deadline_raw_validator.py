import re

from app.intelligence.extracted_task import ExtractedTask
from app.schemas import Segment


class DeadlineRawValidator:
    temporal_signal = re.compile(
        r"\b(?:до|к|через|за|после|по итогам|по окончании|по завершении|"
        r"завтра|сегодня|послезавтра|день|дня|дней|недел|конца|следующ|этой|"
        r"понедельник|вторник|сред|четверг|пятниц|суббот|воскрес|январ|феврал|"
        r"март|апрел|ма[йя]|июн|июл|август|сентябр|октябр|ноябр|декабр|"
        r"дейін|кейін|ішінде|ертең|бүгін|бүрсігүні|апта|келесі|осы|күн|дүйсенбі|сейсенбі|сәрсенбі|"
        r"бейсенбі|жұма|сенбі|жексенбі|қаңтар|ақпан|наурыз|сәуір|мамыр|"
        r"маусым|шілде|тамыз|қыркүйек|қазан|қараша|желтоқсан)\w*\b|\d",
        re.IGNORECASE,
    )

    def clean(
        self, tasks: list[ExtractedTask], segments: list[Segment]
    ) -> tuple[list[ExtractedTask], int]:
        by_id = {segment.id: segment for segment in segments}
        cleaned = []
        discarded = 0
        for task in tasks:
            raw = task.deadline_raw
            sources = [
                by_id[identifier].text
                for identifier in task.source_segment_ids
                if identifier in by_id
            ]
            valid = (
                raw is None
                or (
                    self.temporal_signal.search(raw) is not None
                    and any(
                        " ".join(raw.casefold().split())
                        in " ".join(source.casefold().split())
                        for source in sources
                    )
                )
            )
            if not valid:
                task = task.model_copy(
                    update={"deadline_raw": None, "deadline_date": None}
                )
                discarded += 1
            cleaned.append(task)
        return cleaned, discarded
