import re
from datetime import date, timedelta
from zoneinfo import ZoneInfo

from app.schemas import MeetingContext

MONTHS = (
    ("января", "қаңтар"),
    ("февраля", "ақпан"),
    ("марта", "наурыз"),
    ("апреля", "сәуір"),
    ("мая", "мамыр"),
    ("июня", "маусым"),
    ("июля", "шілде"),
    ("августа", "тамыз"),
    ("сентября", "қыркүйек"),
    ("октября", "қазан"),
    ("ноября", "қараша"),
    ("декабря", "желтоқсан"),
)
WEEKDAYS = (
    ("понедельника", "понедельнику", "дүйсенбі"),
    ("вторника", "вторнику", "сейсенбі"),
    ("среды", "среде", "сәрсенбі"),
    ("четверга", "четвергу", "бейсенбі"),
    ("пятницы", "пятнице", "жұма"),
    ("субботы", "субботе", "сенбі"),
    ("воскресенья", "воскресенью", "жексенбі"),
)
COUNTS = {
    "один": 1,
    "одну": 1,
    "одной": 1,
    "неделю": 1,
    "бір": 1,
    "два": 2,
    "две": 2,
    "екі": 2,
    "три": 3,
    "үш": 3,
    "четыре": 4,
    "төрт": 4,
    "пять": 5,
    "бес": 5,
    "шесть": 6,
    "алты": 6,
    "семь": 7,
    "жеті": 7,
    "восемь": 8,
    "сегіз": 8,
    "девять": 9,
    "тоғыз": 9,
    "десять": 10,
    "он": 10,
}
ORDINALS = (
    "первому",
    "второму",
    "третьему",
    "четвёртому",
    "пятому",
    "шестому",
    "седьмому",
    "восьмому",
    "девятому",
    "десятому",
    "одиннадцатому",
    "двенадцатому",
    "тринадцатому",
    "четырнадцатому",
    "пятнадцатому",
    "шестнадцатому",
    "семнадцатому",
    "восемнадцатому",
    "девятнадцатому",
    "двадцатому",
    "двадцать первому",
    "двадцать второму",
    "двадцать третьему",
    "двадцать четвёртому",
    "двадцать пятому",
    "двадцать шестому",
    "двадцать седьмому",
    "двадцать восьмому",
    "двадцать девятому",
    "тридцатому",
    "тридцать первому",
)


class DeadlineNormalizer:
    def is_deadline_phrase(self, raw: str) -> bool:
        return bool(
            re.search(
                r"\b(?:до|к|после|через|за|завтра|послезавтра|сегодня|сразу|немедленно|когда|по итогам|по окончании|по завершении|на следующ|на этой|текущ|келесі|осы|ертең|бүгін|бүрсігүні)\b|\d|недел|месяц|дня|дней|күн|апта|дейін|кейін|ішінде|пятниц|сред[аые]|октябр|сентябр",
                raw.casefold(),
            )
        )

    def normalize(self, raw: str | None, context: MeetingContext) -> date | None:
        if raw is None:
            return None
        text = " ".join(raw.casefold().replace("ё", "е").split()).strip(" .")
        reference = context.started_at
        if reference is not None and context.time_zone is not None:
            reference = reference.astimezone(ZoneInfo(context.time_zone))
        base = reference.date() if reference else None
        try:
            return self._parse(text, base)
        except (ValueError, OverflowError):
            return None

    def _parse(self, text: str, base: date | None) -> date | None:
        if re.search(
            r"(?:до конца недели|к концу недели|на этой неделе|на следующей неделе|"
            r"апта соңына дейін|осы аптада|келесі аптада|после совещания|"
            r"по итогам|по окончании|по завершении)",
            text,
        ):
            return None
        date_text = re.sub(r"^(?:до|к)\s+", "", text)
        date_text = re.sub(r"(?:-?(?:ға|ге|қа|ке))?\s+дейін$", "", date_text)
        explicit = re.fullmatch(r"(\d{4})-(\d{2})-(\d{2})", date_text)
        if explicit:
            return date(*map(int, explicit.groups()))
        numeric = re.fullmatch(r"(\d{1,2})[./](\d{1,2})(?:[./](\d{4}))?", date_text)
        if numeric:
            day, month, year = numeric.groups()
            if year or base:
                return date(int(year) if year else base.year, int(month), int(day))
            return None
        for index, ordinal in enumerate(ORDINALS, 1):
            normalized = ordinal.replace("ё", "е")
            genitive = normalized.replace("ому", "ого").replace("третьему", "третьего")
            for form in (normalized, genitive):
                if date_text.startswith(form + " "):
                    date_text = str(index) + date_text[len(form) :]
        for month, names in enumerate(MONTHS, 1):
            match = re.fullmatch(
                r"(\d{1,2})\s+(?:"
                + "|".join(names)
                + r")(?:\s+(\d{4})(?:\s+(?:года|жыл))?)?",
                date_text,
            )
            if match:
                day, year = match.groups()
                return (
                    date(int(year) if year else base.year, month, int(day))
                    if year or base
                    else None
                )
        if base is None:
            return None
        day_offsets = {
            "сегодня": 0,
            "завтра": 1,
            "послезавтра": 2,
            "бүгін": 0,
            "ертең": 1,
            "бүрсігүні": 2,
        }
        if date_text in day_offsets:
            return base + timedelta(days=day_offsets[date_text])
        for weekday, names in enumerate(WEEKDAYS):
            if date_text in names:
                return base + timedelta(days=(weekday - base.weekday()) % 7)
            if any(date_text == "следующей " + name for name in names):
                return base + timedelta(days=7 - base.weekday() + weekday)
        relative = re.fullmatch(
            r"(?:через|за|в течение) (?:(\d+|[а-я]+) )?(день|дня|дней|неделю|недели|недель)",
            text,
        )
        if relative is None:
            relative = re.fullmatch(
                r"(\d+|[а-яәғқңөұүһі]+) (күн|апта)(?:дан|ден|тан|тен)? (?:кейін|ішінде)",
                text,
            )
        if relative:
            count_text, unit = relative.groups()
            count = (
                1
                if count_text is None
                else int(count_text)
                if count_text.isdigit()
                else COUNTS.get(count_text)
            )
            if count is None or not 1 <= count <= 3660:
                return None
            if unit.startswith("недел") or unit == "апта":
                return base + timedelta(weeks=count)
            return base + timedelta(days=count)
        return None
