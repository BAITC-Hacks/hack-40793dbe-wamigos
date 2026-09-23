package kz.hackalem.wamigos.meeting.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kz.hackalem.wamigos.error.ErrorCode;
import kz.hackalem.wamigos.meeting.domain.ProcessingStage;
import kz.hackalem.wamigos.meeting.dto.CreateMeetingJobRequest;
import kz.hackalem.wamigos.meeting.dto.MeetingJobDto;
import kz.hackalem.wamigos.meeting.dto.MeetingResultResponse;

public interface MeetingJobService {

    MeetingJobDto create(CreateMeetingJobRequest request);

    boolean hasQueueCapacity(Integer capacity);

    Optional<MeetingJobDto> claimNextQueued(Instant now);

    boolean updateStage(UUID id, ProcessingStage stage);

    boolean complete(UUID id, MeetingResultResponse result, Instant now, Instant expiresAt);

    boolean failIfProcessing(UUID id, ErrorCode code, String message, Instant now, Instant expiresAt);

    MeetingJobDto getAccessible(UUID id, String accessTokenHash, Instant now);

    Integer recoverInterrupted(Instant now, Instant expiresAt);

    Integer failTimedOut(Instant cutoff, Instant now, Instant expiresAt);

    List<MeetingJobDto> findExpired(Instant now);

    boolean deleteExpired(UUID id, Instant now);
}
