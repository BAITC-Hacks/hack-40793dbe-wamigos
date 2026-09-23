package kz.hackalem.wamigos.meeting.dto;

import java.time.Instant;
import java.util.UUID;
import kz.hackalem.wamigos.meeting.domain.MeetingStatus;
import kz.hackalem.wamigos.meeting.domain.ProcessingStage;
import lombok.Builder;

@Builder
public record MeetingResponse(
        UUID id,
        String title,
        Instant startedAt,
        String timeZone,
        Instant createdAt,
        MeetingStatus status,
        ProcessingStage stage,
        Instant expiresAt,
        MeetingFailureResponse error,
        MeetingResultResponse result
) {
}
