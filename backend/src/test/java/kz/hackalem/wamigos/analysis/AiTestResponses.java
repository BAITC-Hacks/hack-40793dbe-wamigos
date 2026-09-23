package kz.hackalem.wamigos.analysis;

public final class AiTestResponses {

    private AiTestResponses() {
    }

    public static String valid() {
        return """
                {
                  "durationMs": 12000,
                  "summary": "Поручено подготовить отчёт и тексеру деректерді.",
                  "speakers": [{"id": "s1", "name": "Влад"}],
                  "segments": [
                    {
                      "id": "seg1",
                      "speakerId": "s1",
                      "startMs": 0,
                      "endMs": 5000,
                      "text": "Арнур, подготовь отчёт.",
                      "tags": ["TASK"]
                    },
                    {
                      "id": "seg2",
                      "speakerId": "s1",
                      "startMs": 5000,
                      "endMs": 12000,
                      "text": "Ә Ғ Қ Ң Ө Ұ Ү Һ І",
                      "tags": ["PROBLEM"]
                    }
                  ],
                  "tasks": [
                    {
                      "id": "t1",
                      "text": "Подготовить отчёт",
                      "assigneeName": "Арнур",
                      "assignerName": "Влад",
                      "deadlineRaw": null,
                      "deadlineDate": null,
                      "sourceSegmentIds": ["seg1"]
                    }
                  ],
                  "problems": [
                    {
                      "id": "p1",
                      "text": "Проверить данные",
                      "reportedBy": "Влад",
                      "sourceSegmentIds": ["seg2"]
                    }
                  ],
                  "diagnostics": {"model": "test-only"}
                }
                """;
    }

    public static String invalidSpeakerReference() {
        return valid().replace("\"speakerId\": \"s1\"", "\"speakerId\": \"missing\"");
    }

    public static String brokenSourceReference() {
        return valid().replace("[\"seg1\"]", "[\"missing\"]");
    }
}
