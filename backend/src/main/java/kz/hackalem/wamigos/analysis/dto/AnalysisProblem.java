package kz.hackalem.wamigos.analysis.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record AnalysisProblem(
        String id,
        String text,
        String reportedBy,
        List<String> sourceSegmentIds
) {
}
