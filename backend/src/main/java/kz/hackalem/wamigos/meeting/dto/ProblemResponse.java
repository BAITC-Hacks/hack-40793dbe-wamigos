package kz.hackalem.wamigos.meeting.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record ProblemResponse(
        String id,
        String text,
        String reportedBy,
        List<String> sourceSegmentIds
) {
}
