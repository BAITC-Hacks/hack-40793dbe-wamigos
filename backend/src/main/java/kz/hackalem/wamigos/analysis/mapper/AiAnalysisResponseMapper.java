package kz.hackalem.wamigos.analysis.mapper;

import java.util.List;
import kz.hackalem.wamigos.analysis.dto.AnalysisProblem;
import kz.hackalem.wamigos.analysis.dto.AnalysisSegment;
import kz.hackalem.wamigos.analysis.dto.AnalysisSpeaker;
import kz.hackalem.wamigos.analysis.dto.AnalysisTask;
import kz.hackalem.wamigos.analysis.dto.MeetingAnalysisOutput;
import kz.hackalem.wamigos.analysis.port.MeetingAnalysisException;
import kz.hackalem.wamigos.analysis.transport.AiAnalysisResponse;
import kz.hackalem.wamigos.analysis.transport.AiProblemResponse;
import kz.hackalem.wamigos.analysis.transport.AiSegmentResponse;
import kz.hackalem.wamigos.analysis.transport.AiSpeakerResponse;
import kz.hackalem.wamigos.analysis.transport.AiTaskResponse;
import kz.hackalem.wamigos.error.ErrorCode;
import kz.hackalem.wamigos.meeting.domain.SegmentTag;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisResponseMapper {

    public MeetingAnalysisOutput toOutput(AiAnalysisResponse response) {
        if (response == null) {
            throw invalidResponse();
        }
        return MeetingAnalysisOutput.builder()
                .durationMs(response.durationMs())
                .summary(response.summary())
                .speakers(mapList(response.speakers(), this::toSpeaker))
                .segments(mapList(response.segments(), this::toSegment))
                .tasks(mapList(response.tasks(), this::toTask))
                .problems(mapList(response.problems(), this::toProblem))
                .build();
    }

    private AnalysisSpeaker toSpeaker(AiSpeakerResponse speaker) {
        return AnalysisSpeaker.builder()
                .id(speaker.id())
                .name(speaker.name())
                .build();
    }

    private AnalysisSegment toSegment(AiSegmentResponse segment) {
        return AnalysisSegment.builder()
                .id(segment.id())
                .speakerId(segment.speakerId())
                .startMs(segment.startMs())
                .endMs(segment.endMs())
                .text(segment.text())
                .tags(mapTags(segment.tags()))
                .build();
    }

    private AnalysisTask toTask(AiTaskResponse task) {
        return AnalysisTask.builder()
                .id(task.id())
                .text(task.text())
                .assigneeName(task.assigneeName())
                .assignerName(task.assignerName())
                .deadlineRaw(task.deadlineRaw())
                .deadlineDate(task.deadlineDate())
                .sourceSegmentIds(copy(task.sourceSegmentIds()))
                .build();
    }

    private AnalysisProblem toProblem(AiProblemResponse problem) {
        return AnalysisProblem.builder()
                .id(problem.id())
                .text(problem.text())
                .reportedBy(problem.reportedBy())
                .sourceSegmentIds(copy(problem.sourceSegmentIds()))
                .build();
    }

    private List<SegmentTag> mapTags(List<String> tags) {
        if (tags == null) {
            return null;
        }
        try {
            return tags.stream().map(SegmentTag::valueOf).toList();
        } catch (IllegalArgumentException exception) {
            throw invalidResponse();
        }
    }

    private <S, T> List<T> mapList(List<S> source, java.util.function.Function<S, T> mapper) {
        return source == null ? null : source.stream().map(mapper).toList();
    }

    private <T> List<T> copy(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }

    private MeetingAnalysisException invalidResponse() {
        return new MeetingAnalysisException(
                ErrorCode.AI_PROCESSING_FAILED,
                "AI-сервис вернул некорректный результат."
        );
    }
}
