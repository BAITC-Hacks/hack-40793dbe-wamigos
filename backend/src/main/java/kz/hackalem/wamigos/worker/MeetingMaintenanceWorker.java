package kz.hackalem.wamigos.worker;

import java.time.Clock;
import java.time.Instant;
import kz.hackalem.wamigos.config.CleanupProperties;
import kz.hackalem.wamigos.config.ProcessingProperties;
import kz.hackalem.wamigos.meeting.dto.MeetingJobDto;
import kz.hackalem.wamigos.meeting.service.MeetingJobService;
import kz.hackalem.wamigos.storage.port.StoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingMaintenanceWorker {

    private final MeetingJobService meetingJobService;
    private final StoragePort storagePort;
    private final ProcessingProperties processingProperties;
    private final CleanupProperties cleanupProperties;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        if (!processingProperties.startupRecoveryEnabled()) {
            return;
        }
        Instant now = clock.instant();
        Integer recovered = meetingJobService.recoverInterrupted(now, now.plus(cleanupProperties.retention()));
        if (recovered > 0) {
            log.warn("Marked {} interrupted meeting jobs as failed", recovered);
        }
    }

    @Scheduled(fixedDelayString = "${app.processing.timeout-check-interval}")
    public void failTimedOutJobs() {
        Instant now = clock.instant();
        Integer failed = meetingJobService.failTimedOut(
                now.minus(processingProperties.timeout()),
                now,
                now.plus(cleanupProperties.retention())
        );
        if (failed > 0) {
            log.warn("Marked {} timed out meeting jobs as failed", failed);
        }
    }

    @Scheduled(fixedDelayString = "${app.cleanup.interval}")
    public void cleanupExpiredJobs() {
        Instant now = clock.instant();
        for (MeetingJobDto job : meetingJobService.findExpired(now)) {
            cleanup(job, now);
        }
    }

    private void cleanup(MeetingJobDto job, Instant now) {
        try {
            storagePort.delete(job.sourcePath());
            meetingJobService.deleteExpired(job.id(), now);
        } catch (RuntimeException exception) {
            log.warn("Cleanup failed for expired meeting job {}", job.id(), exception);
        }
    }
}
