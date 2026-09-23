package kz.hackalem.wamigos.meeting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import kz.hackalem.wamigos.error.ErrorCode;
import kz.hackalem.wamigos.meeting.dto.MeetingResultResponse;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "meeting_jobs")
public class MeetingJob {

    @Id
    private UUID id;

    @Column(name = "access_token_hash", nullable = false, length = 64)
    private String accessTokenHash;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingSource source;

    @Column(name = "meeting_started_at")
    private Instant startedAt;

    @Column(name = "meeting_timezone", length = 100)
    private String timeZone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private ProcessingStage stage;

    @Column(name = "source_path", nullable = false)
    private String sourcePath;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "duration_ms")
    private Long durationMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private MeetingResultResponse result;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code", length = 60)
    private ErrorCode errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
