package kz.hackalem.wamigos.meeting.processor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import kz.hackalem.wamigos.config.QueueProperties;
import kz.hackalem.wamigos.error.QueueFullException;
import kz.hackalem.wamigos.error.StorageException;
import kz.hackalem.wamigos.meeting.dto.CreateMeetingJobRequest;
import kz.hackalem.wamigos.meeting.dto.CreateMeetingRequest;
import kz.hackalem.wamigos.meeting.dto.MeetingCreatedResponse;
import kz.hackalem.wamigos.meeting.dto.MeetingFailureResponse;
import kz.hackalem.wamigos.meeting.dto.MeetingJobDto;
import kz.hackalem.wamigos.meeting.dto.MeetingResponse;
import kz.hackalem.wamigos.meeting.service.AccessTokenManager;
import kz.hackalem.wamigos.meeting.service.MeetingJobService;
import kz.hackalem.wamigos.meeting.validation.MediaTypeDetector;
import kz.hackalem.wamigos.meeting.validation.MeetingRequestValidator;
import kz.hackalem.wamigos.storage.port.StoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MeetingProcessor {

    private static final String DEFAULT_TITLE = "Совещание";

    private final Object submissionLock = new Object();
    private final MeetingJobService meetingJobService;
    private final StoragePort storagePort;
    private final AccessTokenManager accessTokenManager;
    private final MediaTypeDetector mediaTypeDetector;
    private final MeetingRequestValidator meetingRequestValidator;
    private final QueueProperties queueProperties;
    private final Clock clock;

    public MeetingCreatedResponse create(CreateMeetingRequest request) {
        meetingRequestValidator.validateBasic(request);
        String contentType = mediaTypeDetector.detect(request.getFile());
        meetingRequestValidator.validateMediaType(request, contentType);

        synchronized (submissionLock) {
            if (!meetingJobService.hasQueueCapacity(queueProperties.capacity())) {
                throw new QueueFullException();
            }
            return storeAndCreate(request, contentType);
        }
    }

    public MeetingResponse get(UUID id, String authorization) {
        String tokenHash = accessTokenManager.extractAndHash(authorization);
        MeetingJobDto job = meetingJobService.getAccessible(id, tokenHash, clock.instant());
        MeetingFailureResponse error = job.errorCode() == null
                ? null
                : MeetingFailureResponse.builder()
                        .code(job.errorCode())
                        .message(job.errorMessage())
                        .build();
        return MeetingResponse.builder()
                .id(job.id())
                .title(job.title())
                .startedAt(job.startedAt())
                .timeZone(job.timeZone())
                .createdAt(job.createdAt())
                .status(job.status())
                .stage(job.stage())
                .expiresAt(job.expiresAt())
                .error(error)
                .result(job.result())
                .build();
    }

    private MeetingCreatedResponse storeAndCreate(CreateMeetingRequest request, String contentType) {
        String storageKey = null;
        try (InputStream inputStream = request.getFile().getInputStream()) {
            storageKey = storagePort.store(inputStream, meetingRequestValidator.extensionFor(contentType));
            Instant now = clock.instant();
            String accessToken = accessTokenManager.generate();
            MeetingJobDto job = meetingJobService.create(CreateMeetingJobRequest.builder()
                    .id(UUID.randomUUID())
                    .accessTokenHash(accessTokenManager.hash(accessToken))
                    .title(resolveTitle(request))
                    .source(request.getSource())
                    .startedAt(request.getStartedAt() == null ? null : request.getStartedAt().toInstant())
                    .timeZone(resolveTimeZone(request))
                    .createdAt(now)
                    .sourcePath(storageKey)
                    .contentType(contentType)
                    .sizeBytes(request.getFile().getSize())
                    .build());
            return MeetingCreatedResponse.builder()
                    .id(job.id())
                    .accessToken(accessToken)
                    .status(job.status())
                    .title(job.title())
                    .createdAt(job.createdAt())
                    .expiresAt(job.expiresAt())
                    .build();
        } catch (IOException exception) {
            deleteAfterFailure(storageKey, exception);
            throw new StorageException("Не удалось прочитать загруженную запись.", exception);
        } catch (RuntimeException exception) {
            deleteAfterFailure(storageKey, exception);
            throw exception;
        }
    }

    private String resolveTitle(CreateMeetingRequest request) {
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            return request.getTitle().trim();
        }
        String fileName = safeFileName(request.getFile().getOriginalFilename());
        if (fileName.isBlank()) {
            return DEFAULT_TITLE;
        }
        return fileName.length() <= 200 ? fileName : fileName.substring(0, 200);
    }

    private String resolveTimeZone(CreateMeetingRequest request) {
        if (request.getTimeZone() != null && !request.getTimeZone().isBlank()) {
            return request.getTimeZone().trim();
        }
        return request.getStartedAt() == null ? null : request.getStartedAt().getOffset().getId();
    }

    private String safeFileName(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        try {
            Path fileName = Path.of(originalFilename.replace('\\', '/')).getFileName();
            return fileName == null ? "" : fileName.toString().trim();
        } catch (InvalidPathException exception) {
            return DEFAULT_TITLE;
        }
    }

    private void deleteAfterFailure(String storageKey, Exception originalException) {
        if (storageKey == null) {
            return;
        }
        try {
            storagePort.delete(storageKey);
        } catch (RuntimeException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }
}
