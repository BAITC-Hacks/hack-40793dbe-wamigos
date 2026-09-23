package kz.hackalem.wamigos.meeting.dto;

import java.time.Instant;
import java.util.UUID;
import kz.hackalem.wamigos.meeting.domain.MeetingStatus;
import lombok.Builder;

@Builder
public record MeetingCreatedResponse(
        UUID id,
        String accessToken,
        MeetingStatus status,
        String title,
        Instant createdAt,
        Instant expiresAt
) {
}
