package kz.hackalem.wamigos.analysis.mapper;

import java.util.List;
import kz.hackalem.wamigos.analysis.dto.AnalysisProblem;
import kz.hackalem.wamigos.analysis.dto.AnalysisSegment;
import kz.hackalem.wamigos.analysis.dto.AnalysisSpeaker;
import kz.hackalem.wamigos.analysis.dto.AnalysisTask;
import kz.hackalem.wamigos.analysis.dto.MeetingAnalysisOutput;
import kz.hackalem.wamigos.meeting.dto.MeetingResultResponse;
import kz.hackalem.wamigos.meeting.dto.ProblemResponse;
import kz.hackalem.wamigos.meeting.dto.SegmentResponse;
import kz.hackalem.wamigos.meeting.dto.SpeakerResponse;
import kz.hackalem.wamigos.meeting.dto.TaskResponse;
import org.springframework.stereotype.Component;

@Component
public class MeetingAnalysisResultMapper {

    public MeetingResultResponse toPublicResult(MeetingAnalysisOutput output) {
        return MeetingResultResponse.builder()
                .durationMs(output.durationMs())
                .summary(output.summary())
                .speakers(output.speakers().stream().map(this::toSpeaker).toList())
                .segments(output.segments().stream().map(this::toSegment).toList())
                .tasks(output.tasks().stream().map(this::toTask).toList())
                .problems(output.problems().stream().map(this::toProblem).toList())
                .build();
    }

    private SpeakerResponse toSpeaker(AnalysisSpeaker speaker) {
        return SpeakerResponse.builder().id(speaker.id()).name(speaker.name()).build();
    }

    private SegmentResponse toSegment(AnalysisSegment segment) {
        return SegmentResponse.builder()
                .id(segment.id())
                .speakerId(segment.speakerId())
                .startMs(segment.startMs())
                .endMs(segment.endMs())
                .text(segment.text())
                .tags(List.copyOf(segment.tags()))
                .build();
    }

    private TaskResponse toTask(AnalysisTask task) {
        return TaskResponse.builder()
                .id(task.id())
                .text(task.text())
                .assigneeName(task.assigneeName())
                .assignerName(task.assignerName())
                .deadlineRaw(task.deadlineRaw())
                .deadlineDate(task.deadlineDate())
                .sourceSegmentIds(List.copyOf(task.sourceSegmentIds()))
                .build();
    }

    private ProblemResponse toProblem(AnalysisProblem problem) {
        return ProblemResponse.builder()
                .id(problem.id())
                .text(problem.text())
                .reportedBy(problem.reportedBy())
                .sourceSegmentIds(List.copyOf(problem.sourceSegmentIds()))
                .build();
    }
}
