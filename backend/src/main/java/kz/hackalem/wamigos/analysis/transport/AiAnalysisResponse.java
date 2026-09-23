package kz.hackalem.wamigos.analysis.transport;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.Builder;

@Builder
public record AiAnalysisResponse(
        Long durationMs,
        String summary,
        List<AiSpeakerResponse> speakers,
        List<AiSegmentResponse> segments,
        List<AiTaskResponse> tasks,
        List<AiProblemResponse> problems,
        JsonNode diagnostics
) {
}
