package kz.hackalem.wamigos.analysis.dto;

import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import kz.hackalem.wamigos.meeting.domain.MeetingSource;
import lombok.Builder;

@Builder
public record MeetingAnalysisRequest(
        UUID meetingId,
        Path sourcePath,
        String contentType,
        Long sizeBytes,
        MeetingSource source,
        Instant startedAt,
        String timeZone,
        Duration maximumDuration
) {
}
