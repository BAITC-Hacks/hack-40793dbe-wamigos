package kz.hackalem.wamigos.meeting.dto;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

@Builder
public record TaskResponse(
        String id,
        String text,
        String assigneeName,
        String assignerName,
        String deadlineRaw,
        LocalDate deadlineDate,
        List<String> sourceSegmentIds
) {
}
