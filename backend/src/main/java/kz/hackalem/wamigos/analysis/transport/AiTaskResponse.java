package kz.hackalem.wamigos.analysis.transport;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

@Builder
public record AiTaskResponse(
        String id,
        String text,
        String assigneeName,
        String assignerName,
        String deadlineRaw,
        LocalDate deadlineDate,
        List<String> sourceSegmentIds
) {
}
