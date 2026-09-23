package kz.hackalem.wamigos.meeting;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import kz.hackalem.wamigos.analysis.port.MeetingAnalysisException;
import kz.hackalem.wamigos.config.UploadProperties;
import kz.hackalem.wamigos.error.ErrorCode;
import kz.hackalem.wamigos.meeting.domain.SegmentTag;
import kz.hackalem.wamigos.meeting.dto.MeetingResultResponse;
import kz.hackalem.wamigos.meeting.dto.ProblemResponse;
import kz.hackalem.wamigos.meeting.dto.SegmentResponse;
import kz.hackalem.wamigos.meeting.dto.SpeakerResponse;
import kz.hackalem.wamigos.meeting.dto.TaskResponse;
import kz.hackalem.wamigos.meeting.validation.MeetingResultValidator;
import org.junit.jupiter.api.Test;

class MeetingResultValidatorTest {

    private final MeetingResultValidator validator = new MeetingResultValidator(
            new UploadProperties(524_288_000L, Duration.ofMinutes(60))
    );

    @Test
    void acceptsValidResult() {
        assertThatCode(() -> validator.validate(validResult())).doesNotThrowAnyException();
    }

    @Test
    void acceptsZeroDurationResult() {
        MeetingResultResponse result = MeetingResultResponse.builder()
                .durationMs(0L)
                .summary("")
                .speakers(List.of())
                .segments(List.of())
                .tasks(List.of())
                .problems(List.of())
                .build();

        assertThatCode(() -> validator.validate(result)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownSourceSegment() {
        MeetingResultResponse result = MeetingResultResponse.builder()
                .durationMs(1_000L)
                .summary("Итоги")
                .speakers(List.of())
                .segments(List.of())
                .tasks(List.of(TaskResponse.builder()
                        .id("t1")
                        .text("Задача")
                        .sourceSegmentIds(List.of("missing"))
                        .build()))
                .problems(List.of())
                .build();

        assertThatThrownBy(() -> validator.validate(result))
                .isInstanceOf(MeetingAnalysisException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.AI_PROCESSING_FAILED);
    }

    @Test
    void rejectsDurationOverLimitWithDocumentedCode() {
        MeetingResultResponse result = MeetingResultResponse.builder()
                .durationMs(Duration.ofMinutes(61).toMillis())
                .summary("Итоги")
                .speakers(List.of())
                .segments(List.of())
                .tasks(List.of())
                .problems(List.of())
                .build();

        assertThatThrownBy(() -> validator.validate(result))
                .isInstanceOf(MeetingAnalysisException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.DURATION_LIMIT_EXCEEDED);
    }

    private MeetingResultResponse validResult() {
        return MeetingResultResponse.builder()
                .durationMs(2_000L)
                .summary("Итоги")
                .speakers(List.of(SpeakerResponse.builder().id("s1").name("Алия").build()))
                .segments(List.of(SegmentResponse.builder()
                        .id("seg1")
                        .speakerId("s1")
                        .startMs(0L)
                        .endMs(2_000L)
                        .text("Подготовить отчёт")
                        .tags(List.of(SegmentTag.TASK))
                        .build()))
                .tasks(List.of(TaskResponse.builder()
                        .id("t1")
                        .text("Подготовить отчёт")
                        .sourceSegmentIds(List.of("seg1"))
                        .build()))
                .problems(List.of(ProblemResponse.builder()
                        .id("p1")
                        .text("Нет исходных данных")
                        .sourceSegmentIds(List.of("seg1"))
                        .build()))
                .build();
    }
}
