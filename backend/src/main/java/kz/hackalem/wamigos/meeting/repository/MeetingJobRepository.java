package kz.hackalem.wamigos.meeting.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kz.hackalem.wamigos.meeting.domain.MeetingJob;
import kz.hackalem.wamigos.meeting.domain.MeetingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingJobRepository extends JpaRepository<MeetingJob, UUID> {

    long countByStatus(MeetingStatus status);

    @Query(value = "SELECT * FROM meeting_jobs WHERE status = 'QUEUED' ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1", nativeQuery = true)
    Optional<MeetingJob> findNextQueuedForUpdate();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM MeetingJob job WHERE job.id = :id")
    Optional<MeetingJob> findByIdForUpdate(@Param("id") UUID id);

    List<MeetingJob> findTop100ByStatusInAndExpiresAtLessThanEqualOrderByExpiresAt(
            Collection<MeetingStatus> statuses,
            Instant expiresAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<MeetingJob> findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAt(
            MeetingStatus status,
            Instant updatedAt
    );

    List<MeetingJob> findByStatus(MeetingStatus status);
}
