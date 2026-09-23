package kz.hackalem.wamigos.worker;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import kz.hackalem.wamigos.analysis.dto.MeetingAnalysisOutput;
import kz.hackalem.wamigos.analysis.dto.MeetingAnalysisRequest;
import kz.hackalem.wamigos.analysis.mapper.MeetingAnalysisResultMapper;
import kz.hackalem.wamigos.analysis.port.MeetingAnalysisException;
import kz.hackalem.wamigos.analysis.port.MeetingAnalysisPort;
import kz.hackalem.wamigos.config.CleanupProperties;
import kz.hackalem.wamigos.error.ErrorCode;
import kz.hackalem.wamigos.error.StorageException;
import kz.hackalem.wamigos.meeting.domain.ProcessingStage;
import kz.hackalem.wamigos.meeting.dto.MeetingJobDto;
import kz.hackalem.wamigos.meeting.dto.MeetingResultResponse;
import kz.hackalem.wamigos.meeting.service.MeetingJobService;
import kz.hackalem.wamigos.meeting.validation.MeetingResultValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.processing.worker-enabled", havingValue = "true", matchIfMissing = true)
public class MeetingProcessingWorker {

    private final MeetingJobService meetingJobService;
    private final MeetingAnalysisPort meetingAnalysisPort;
    private final MeetingAnalysisResultMapper meetingAnalysisResultMapper;
    private final MeetingResultValidator meetingResultValidator;
    private final CleanupProperties cleanupProperties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.queue.poll-interval}")
    public void processNext() {
        Optional<MeetingJobDto> nextJob = meetingJobService.claimNextQueued(clock.instant());
        nextJob.ifPresent(this::process);
    }

    private void process(MeetingJobDto job) {
        try {
            if (!meetingJobService.updateStage(job.id(), ProcessingStage.ANALYZING)) {
                return;
            }
            MeetingAnalysisOutput output = meetingAnalysisPort.analyze(MeetingAnalysisRequest.builder()
                    .meetingId(job.id())
                    .storageKey(job.sourcePath())
                    .contentType(job.contentType())
                    .startedAt(job.startedAt())
                    .timeZone(job.timeZone())
                    .build());
            MeetingResultResponse result = meetingAnalysisResultMapper.toPublicResult(output);
            meetingResultValidator.validate(result);
            if (!meetingJobService.updateStage(job.id(), ProcessingStage.FINALIZING)) {
                return;
            }
            Instant completedAt = clock.instant();
            meetingJobService.complete(
                    job.id(),
                    result,
                    completedAt,
                    completedAt.plus(cleanupProperties.retention())
            );
        } catch (MeetingAnalysisException exception) {
            fail(job, exception.getCode(), exception.getMessage());
        } catch (StorageException exception) {
            fail(job, ErrorCode.STORAGE_ERROR, exception.getMessage());
        } catch (Exception exception) {
            log.error("Meeting processing failed for job {}", job.id(), exception);
            fail(job, ErrorCode.AI_PROCESSING_FAILED, "Не удалось обработать запись.");
        }
    }

    private void fail(MeetingJobDto job, ErrorCode code, String message) {
        Instant failedAt = clock.instant();
        meetingJobService.failIfProcessing(
                job.id(),
                code,
                message,
                failedAt,
                failedAt.plus(cleanupProperties.retention())
        );
    }
}
