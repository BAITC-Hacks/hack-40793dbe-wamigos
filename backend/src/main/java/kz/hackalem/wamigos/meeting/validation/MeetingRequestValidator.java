package kz.hackalem.wamigos.meeting.validation;

import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import kz.hackalem.wamigos.config.UploadProperties;
import kz.hackalem.wamigos.error.FileTooLargeException;
import kz.hackalem.wamigos.error.UnsupportedMediaTypeException;
import kz.hackalem.wamigos.error.ValidationException;
import kz.hackalem.wamigos.meeting.domain.MeetingSource;
import kz.hackalem.wamigos.meeting.dto.CreateMeetingRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MeetingRequestValidator {

    private static final Map<MeetingSource, Set<String>> ALLOWED_MEDIA_TYPES = Map.of(
            MeetingSource.UPLOAD, Set.of("audio/mpeg", "audio/mp3", "video/mp4", "audio/mp4", "application/mp4"),
            MeetingSource.MICROPHONE, Set.of(
                    "audio/mpeg",
                    "audio/mp3",
                    "video/mp4",
                    "audio/mp4",
                    "application/mp4",
                    "audio/webm",
                    "video/webm",
                    "audio/ogg",
                    "application/ogg"
            )
    );

    private final UploadProperties uploadProperties;

    public void validateBasic(CreateMeetingRequest request) {
        if (request.getFile() == null || request.getFile().isEmpty()) {
            throw new ValidationException("Поле file обязательно и не должно быть пустым.");
        }
        if (request.getFile().getSize() > uploadProperties.maxBytes()) {
            throw new FileTooLargeException();
        }
        if (request.getSource() == null) {
            throw new ValidationException("Поле source обязательно.");
        }
        validateTitle(request.getTitle());
        validateTimeZone(request.getTimeZone());
    }

    public void validateMediaType(CreateMeetingRequest request, String detectedMediaType) {
        Set<String> allowed = ALLOWED_MEDIA_TYPES.get(request.getSource());
        if (allowed == null || !allowed.contains(detectedMediaType)) {
            throw new UnsupportedMediaTypeException();
        }
    }

    public String extensionFor(String mediaType) {
        return switch (mediaType) {
            case "audio/mpeg", "audio/mp3" -> ".mp3";
            case "video/mp4", "audio/mp4", "application/mp4" -> ".mp4";
            case "audio/webm", "video/webm" -> ".webm";
            case "audio/ogg", "application/ogg" -> ".ogg";
            default -> throw new UnsupportedMediaTypeException();
        };
    }

    private void validateTitle(String title) {
        if (title != null && title.trim().length() > 200) {
            throw new ValidationException("Название должно содержать не более 200 символов.");
        }
    }

    private void validateTimeZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return;
        }
        if (!ZoneId.getAvailableZoneIds().contains(timeZone.trim())) {
            throw new ValidationException("Поле timeZone должно содержать IANA-идентификатор часового пояса.");
        }
    }
}
