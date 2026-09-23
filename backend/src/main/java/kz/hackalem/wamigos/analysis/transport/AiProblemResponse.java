package kz.hackalem.wamigos.analysis.transport;

import java.util.List;
import lombok.Builder;

@Builder
public record AiProblemResponse(
        String id,
        String text,
        String reportedBy,
        List<String> sourceSegmentIds
) {
}
