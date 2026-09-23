package kz.hackalem.wamigos.analysis.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record MeetingAnalysisOutput(
        Long durationMs,
        String summary,
        List<AnalysisSpeaker> speakers,
        List<AnalysisSegment> segments,
        List<AnalysisTask> tasks,
        List<AnalysisProblem> problems
) {
}
