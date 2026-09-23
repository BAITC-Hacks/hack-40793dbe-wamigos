package kz.hackalem.wamigos.meeting.dto;

import java.time.Instant;
import java.util.UUID;
import kz.hackalem.wamigos.meeting.domain.MeetingSource;
import lombok.Builder;

@Builder
public record CreateMeetingJobRequest(
        UUID id,
        String accessTokenHash,
        String title,
        MeetingSource source,
        Instant startedAt,
        String timeZone,
        Instant createdAt,
        String sourcePath,
        String contentType,
        Long sizeBytes
) {
}
