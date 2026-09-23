import unittest

from pydantic import ValidationError

from app.intelligence.deadline_normalizer import DeadlineNormalizer
from app.schemas import MeetingContext


class DeadlineTests(unittest.TestCase):
    def test_ru_kk_rules(self):
        context = MeetingContext(
            startedAt="2026-09-23T10:00:00+05:00", timeZone="Asia/Almaty"
        )
        cases = {
            "до 30 сентября": "2026-09-30",
            "к пятнадцатому октября": "2026-10-15",
            "до тридцать первого декабря": "2026-12-31",
            "до пятницы": "2026-09-25",
            "к среде": "2026-09-23",
            "через две недели": "2026-10-07",
            "за неделю": "2026-09-30",
            "до конца недели": "2026-09-27",
            "жұмаға дейін": "2026-09-25",
            "ертеңге дейін": "2026-09-24",
            "екі апта ішінде": "2026-10-07",
            "30 қыркүйекке дейін": "2026-09-30",
            "до 31 февраля": None,
            "после совещания с подрядчиками": None,
            "на этой неделе": None,
            "келесі аптада": None,
            "за пять рабочих дней": None,
            None: None,
        }
        for raw, expected in cases.items():
            with self.subTest(raw=raw):
                actual = DeadlineNormalizer().normalize(raw, context)
                self.assertEqual(actual.isoformat() if actual else None, expected)

    def test_no_fabricated_context(self):
        for raw in (
            "до пятницы",
            "ертеңге дейін",
            "до 30 сентября",
            "через две недели",
        ):
            self.assertIsNone(DeadlineNormalizer().normalize(raw, MeetingContext()))
        self.assertEqual(
            str(
                DeadlineNormalizer().normalize("до 30 сентября 2027", MeetingContext())
            ),
            "2027-09-30",
        )
        self.assertEqual(
            str(DeadlineNormalizer().normalize("до 2027-09-30", MeetingContext())),
            "2027-09-30",
        )

    def test_timezone_and_calendar_edges(self):
        context = MeetingContext(
            startedAt="2026-12-31T23:30:00-05:00", timeZone="Asia/Almaty"
        )
        self.assertEqual(
            str(DeadlineNormalizer().normalize("завтра", context)), "2027-01-02"
        )
        offset = MeetingContext(startedAt="2026-12-31T23:30:00-05:00")
        self.assertEqual(
            str(DeadlineNormalizer().normalize("завтра", offset)), "2027-01-01"
        )
        month = MeetingContext(startedAt="2028-01-31T00:00:00Z")
        self.assertEqual(
            str(DeadlineNormalizer().normalize("через месяц", month)), "2028-02-29"
        )

    def test_invalid_context(self):
        for data in (
            {"startedAt": "2026-09-23T12:00:00"},
            {"timeZone": "Mars/City"},
            {"startedAt": 1790146800},
            {"startedAt": "1790146800"},
        ):
            with self.assertRaises(ValidationError):
                MeetingContext.model_validate(data)
