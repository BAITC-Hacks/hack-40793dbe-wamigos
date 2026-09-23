package kz.hackalem.wamigos.meeting.dto;

import java.time.Instant;
import java.util.UUID;
import kz.hackalem.wamigos.error.ErrorCode;
import kz.hackalem.wamigos.meeting.domain.MeetingSource;
import kz.hackalem.wamigos.meeting.domain.MeetingStatus;
import kz.hackalem.wamigos.meeting.domain.ProcessingStage;
import lombok.Builder;

@Builder
public record MeetingJobDto(
        UUID id,
        String accessTokenHash,
        String title,
        MeetingSource source,
        Instant startedAt,
        String timeZone,
        Instant createdAt,
        Instant updatedAt,
        MeetingStatus status,
        ProcessingStage stage,
        String sourcePath,
        String contentType,
        Long sizeBytes,
        Long durationMs,
        MeetingResultResponse result,
        ErrorCode errorCode,
        String errorMessage,
        Instant completedAt,
        Instant expiresAt
) {
}
