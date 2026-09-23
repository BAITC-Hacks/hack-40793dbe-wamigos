package kz.hackalem.wamigos.analysis.adapter;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import kz.hackalem.wamigos.analysis.dto.AnalysisProblem;
import kz.hackalem.wamigos.analysis.dto.AnalysisSegment;
import kz.hackalem.wamigos.analysis.dto.AnalysisSpeaker;
import kz.hackalem.wamigos.analysis.dto.AnalysisTask;
import kz.hackalem.wamigos.analysis.dto.MeetingAnalysisOutput;
import kz.hackalem.wamigos.analysis.dto.MeetingAnalysisRequest;
import kz.hackalem.wamigos.analysis.port.MeetingAnalysisException;
import kz.hackalem.wamigos.analysis.port.MeetingAnalysisPort;
import kz.hackalem.wamigos.config.AiProperties;
import kz.hackalem.wamigos.error.ErrorCode;
import kz.hackalem.wamigos.meeting.domain.SegmentTag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "mock", matchIfMissing = true)
public class MockMeetingAnalysisAdapter implements MeetingAnalysisPort {

    private final AiProperties aiProperties;

    @Override
    public MeetingAnalysisOutput analyze(MeetingAnalysisRequest request) {
        waitForConfiguredDelay(aiProperties.mockDelay());
        if (aiProperties.mockForceFailure()) {
            throw new MeetingAnalysisException(
                    ErrorCode.AI_PROCESSING_FAILED,
                    "Mock-анализ завершён с настроенной ошибкой."
            );
        }

        return MeetingAnalysisOutput.builder()
                .durationMs(12_000L)
                .summary("Поручено подготовить отчёт и проверить данные встречи.")
                .speakers(List.of(
                        AnalysisSpeaker.builder().id("s1").name("Влад").build()
                ))
                .segments(List.of(
                        AnalysisSegment.builder()
                                .id("seg1")
                                .speakerId("s1")
                                .startMs(0L)
                                .endMs(5_000L)
                                .text("Арнур, подготовь отчёт до пятницы.")
                                .tags(List.of(SegmentTag.TASK))
                                .build(),
                        AnalysisSegment.builder()
                                .id("seg2")
                                .speakerId("s1")
                                .startMs(5_000L)
                                .endMs(12_000L)
                                .text("Деректерді тексеру кезінде сәйкессіздік табылды.")
                                .tags(List.of(SegmentTag.PROBLEM))
                                .build()
                ))
                .tasks(List.of(
                        AnalysisTask.builder()
                                .id("t1")
                                .text("Подготовить отчёт")
                                .assigneeName("Арнур")
                                .assignerName("Влад")
                                .deadlineRaw("до 25 сентября 2026 года")
                                .deadlineDate(LocalDate.of(2026, 9, 25))
                                .sourceSegmentIds(List.of("seg1"))
                                .build()
                ))
                .problems(List.of(
                        AnalysisProblem.builder()
                                .id("p1")
                                .text("Обнаружено несоответствие в данных")
                                .reportedBy("Влад")
                                .sourceSegmentIds(List.of("seg2"))
                                .build()
                ))
                .build();
    }

    private void waitForConfiguredDelay(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MeetingAnalysisException(
                    ErrorCode.AI_PROCESSING_FAILED,
                    "Mock-анализ был прерван.",
                    exception
            );
        }
    }
}
