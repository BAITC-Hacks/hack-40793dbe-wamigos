package kz.hackalem.wamigos.meeting.mapper;

import java.time.Instant;
import kz.hackalem.wamigos.error.ErrorCode;
import kz.hackalem.wamigos.meeting.domain.MeetingJob;
import kz.hackalem.wamigos.meeting.domain.MeetingStatus;
import kz.hackalem.wamigos.meeting.domain.ProcessingStage;
import kz.hackalem.wamigos.meeting.dto.CreateMeetingJobRequest;
import kz.hackalem.wamigos.meeting.dto.MeetingJobDto;
import kz.hackalem.wamigos.meeting.dto.MeetingResultResponse;
import org.springframework.stereotype.Component;

@Component
public class MeetingJobMapper {

    public MeetingJob toEntity(CreateMeetingJobRequest request) {
        MeetingJob job = new MeetingJob();
        job.setId(request.id());
        job.setAccessTokenHash(request.accessTokenHash());
        job.setTitle(request.title());
        job.setSource(request.source());
        job.setStartedAt(request.startedAt());
        job.setTimeZone(request.timeZone());
        job.setCreatedAt(request.createdAt());
        job.setUpdatedAt(request.createdAt());
        job.setStatus(MeetingStatus.QUEUED);
        job.setSourcePath(request.sourcePath());
        job.setContentType(request.contentType());
        job.setSizeBytes(request.sizeBytes());
        return job;
    }

    public MeetingJobDto toDto(MeetingJob job) {
        return MeetingJobDto.builder()
                .id(job.getId())
                .accessTokenHash(job.getAccessTokenHash())
                .title(job.getTitle())
                .source(job.getSource())
                .startedAt(job.getStartedAt())
                .timeZone(job.getTimeZone())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .status(job.getStatus())
                .stage(job.getStage())
                .sourcePath(job.getSourcePath())
                .contentType(job.getContentType())
                .sizeBytes(job.getSizeBytes())
                .durationMs(job.getDurationMs())
                .result(job.getResult())
                .errorCode(job.getErrorCode())
                .errorMessage(job.getErrorMessage())
                .completedAt(job.getCompletedAt())
                .expiresAt(job.getExpiresAt())
                .build();
    }

    public void markProcessing(MeetingJob job, Instant now) {
        job.setStatus(MeetingStatus.PROCESSING);
        job.setStage(ProcessingStage.PREPARING_MEDIA);
        job.setUpdatedAt(now);
        job.setResult(null);
        job.setDurationMs(null);
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setCompletedAt(null);
        job.setExpiresAt(null);
    }

    public void updateStage(MeetingJob job, ProcessingStage stage) {
        job.setStage(stage);
    }

    public void complete(MeetingJob job, MeetingResultResponse result, Instant now, Instant expiresAt) {
        job.setStatus(MeetingStatus.COMPLETED);
        job.setStage(null);
        job.setResult(result);
        job.setDurationMs(result.durationMs());
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setCompletedAt(now);
        job.setExpiresAt(expiresAt);
        job.setUpdatedAt(now);
    }

    public void fail(MeetingJob job, ErrorCode code, String message, Instant now, Instant expiresAt) {
        job.setStatus(MeetingStatus.FAILED);
        job.setStage(null);
        job.setResult(null);
        job.setErrorCode(code);
        job.setErrorMessage(message);
        job.setCompletedAt(now);
        job.setExpiresAt(expiresAt);
        job.setUpdatedAt(now);
    }
}
