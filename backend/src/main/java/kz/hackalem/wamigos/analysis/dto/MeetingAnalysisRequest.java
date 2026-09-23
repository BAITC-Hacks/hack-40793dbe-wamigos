package kz.hackalem.wamigos.analysis.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record MeetingAnalysisRequest(
        UUID meetingId,
        String storageKey,
        String contentType,
        Instant startedAt,
        String timeZone
) {
}
