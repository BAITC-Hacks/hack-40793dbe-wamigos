package kz.hackalem.wamigos.meeting.service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kz.hackalem.wamigos.error.ErrorCode;
import kz.hackalem.wamigos.error.NotFoundException;
import kz.hackalem.wamigos.meeting.domain.MeetingJob;
import kz.hackalem.wamigos.meeting.domain.MeetingStatus;
import kz.hackalem.wamigos.meeting.domain.ProcessingStage;
import kz.hackalem.wamigos.meeting.dto.CreateMeetingJobRequest;
import kz.hackalem.wamigos.meeting.dto.MeetingJobDto;
import kz.hackalem.wamigos.meeting.dto.MeetingResultResponse;
import kz.hackalem.wamigos.meeting.mapper.MeetingJobMapper;
import kz.hackalem.wamigos.meeting.repository.MeetingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeetingJobServiceImpl implements MeetingJobService {

    private static final EnumSet<MeetingStatus> FINAL_STATUSES = EnumSet.of(
            MeetingStatus.COMPLETED,
            MeetingStatus.FAILED
    );

    private final MeetingJobRepository meetingJobRepository;
    private final MeetingJobMapper meetingJobMapper;
    private final AccessTokenManager accessTokenManager;

    @Override
    @Transactional
    public MeetingJobDto create(CreateMeetingJobRequest request) {
        MeetingJob job = meetingJobMapper.toEntity(request);
        return meetingJobMapper.toDto(meetingJobRepository.save(job));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasQueueCapacity(Integer capacity) {
        return meetingJobRepository.countByStatus(MeetingStatus.QUEUED) < capacity;
    }

    @Override
    @Transactional
    public Optional<MeetingJobDto> claimNextQueued(Instant now) {
        Optional<MeetingJob> nextJob = meetingJobRepository.findNextQueuedForUpdate();
        nextJob.ifPresent(job -> meetingJobMapper.markProcessing(job, now));
        return nextJob.map(meetingJobMapper::toDto);
    }

    @Override
    @Transactional
    public boolean updateStage(UUID id, ProcessingStage stage) {
        MeetingJob job = getForUpdate(id);
        if (job.getStatus() != MeetingStatus.PROCESSING) {
            return false;
        }
        meetingJobMapper.updateStage(job, stage);
        return true;
    }

    @Override
    @Transactional
    public boolean complete(UUID id, MeetingResultResponse result, Instant now, Instant expiresAt) {
        MeetingJob job = getForUpdate(id);
        if (job.getStatus() != MeetingStatus.PROCESSING) {
            return false;
        }
        meetingJobMapper.complete(job, result, now, expiresAt);
        return true;
    }

    @Override
    @Transactional
    public boolean failIfProcessing(
            UUID id,
            ErrorCode code,
            String message,
            Instant now,
            Instant expiresAt
    ) {
        MeetingJob job = getForUpdate(id);
        if (job.getStatus() != MeetingStatus.PROCESSING) {
            return false;
        }
        meetingJobMapper.fail(job, code, message, now, expiresAt);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingJobDto getAccessible(UUID id, String accessTokenHash, Instant now) {
        MeetingJob job = meetingJobRepository.findById(id).orElseThrow(NotFoundException::new);
        if (!accessTokenManager.matches(job.getAccessTokenHash(), accessTokenHash)
                || isExpired(job, now)) {
            throw new NotFoundException();
        }
        return meetingJobMapper.toDto(job);
    }

    @Override
    @Transactional
    public Integer recoverInterrupted(Instant now, Instant expiresAt) {
        List<MeetingJob> interruptedJobs = meetingJobRepository.findByStatus(MeetingStatus.PROCESSING);
        interruptedJobs.forEach(job -> meetingJobMapper.fail(
                job,
                ErrorCode.PROCESSING_INTERRUPTED,
                "Обработка была прервана перезапуском сервиса.",
                now,
                expiresAt
        ));
        return interruptedJobs.size();
    }

    @Override
    @Transactional
    public Integer failTimedOut(Instant cutoff, Instant now, Instant expiresAt) {
        List<MeetingJob> timedOutJobs = meetingJobRepository
                .findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAt(MeetingStatus.PROCESSING, cutoff);
        timedOutJobs.forEach(job -> meetingJobMapper.fail(
                job,
                ErrorCode.PROCESSING_TIMEOUT,
                "Превышено допустимое время обработки.",
                now,
                expiresAt
        ));
        return timedOutJobs.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetingJobDto> findExpired(Instant now) {
        return meetingJobRepository
                .findTop100ByStatusInAndExpiresAtLessThanEqualOrderByExpiresAt(FINAL_STATUSES, now)
                .stream()
                .map(meetingJobMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public boolean deleteExpired(UUID id, Instant now) {
        Optional<MeetingJob> lockedJob = meetingJobRepository.findByIdForUpdate(id);
        if (lockedJob.isEmpty()) {
            return false;
        }
        MeetingJob job = lockedJob.get();
        if (!FINAL_STATUSES.contains(job.getStatus()) || !isExpired(job, now)) {
            return false;
        }
        meetingJobRepository.delete(job);
        return true;
    }

    private MeetingJob getForUpdate(UUID id) {
        return meetingJobRepository.findByIdForUpdate(id).orElseThrow(NotFoundException::new);
    }

    private boolean isExpired(MeetingJob job, Instant now) {
        return job.getExpiresAt() != null && !job.getExpiresAt().isAfter(now);
    }
}
