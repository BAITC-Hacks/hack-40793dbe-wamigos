package kz.hackalem.wamigos.export.model;

import java.util.List;
import lombok.Builder;

@Builder
public record ExportModel(
        String title,
        String meetingDate,
        List<String> speakers,
        String summary,
        List<ExportTask> tasks,
        List<ExportProblem> problems,
        List<ExportTranscriptLine> transcript
) {
}
