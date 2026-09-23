package kz.hackalem.wamigos.analysis.mapper;

import java.util.EnumSet;
import kz.hackalem.wamigos.analysis.port.MeetingAnalysisException;
import kz.hackalem.wamigos.analysis.transport.AiErrorResponse;
import kz.hackalem.wamigos.error.ErrorCode;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

@Component
public class AiHttpErrorMapper {

    private static final int MAX_SAFE_MESSAGE_LENGTH = 500;
    private static final EnumSet<ErrorCode> MEDIA_ERROR_CODES = EnumSet.of(
            ErrorCode.MEDIA_NOT_DECODABLE,
            ErrorCode.NO_AUDIO_TRACK,
            ErrorCode.DURATION_LIMIT_EXCEEDED
    );

    public MeetingAnalysisException toException(HttpStatusCode status, AiErrorResponse response) {
        ErrorCode responseCode = parseCode(response);
        if (status.value() == 422 && responseCode != null && MEDIA_ERROR_CODES.contains(responseCode)) {
            return new MeetingAnalysisException(responseCode, safeMessage(response, defaultMessage(responseCode)));
        }
        String message = status.value() == 503
                ? "AI-сервис временно недоступен."
                : "Не удалось обработать запись в AI-сервисе.";
        return new MeetingAnalysisException(ErrorCode.AI_PROCESSING_FAILED, message);
    }

    private ErrorCode parseCode(AiErrorResponse response) {
        if (response == null || response.code() == null) {
            return null;
        }
        return MEDIA_ERROR_CODES.stream()
                .filter(code -> code.name().equals(response.code()))
                .findFirst()
                .orElse(null);
    }

    private String safeMessage(AiErrorResponse response, String defaultMessage) {
        if (response == null || response.message() == null || response.message().isBlank()) {
            return defaultMessage;
        }
        String message = response.message().replace('\n', ' ').replace('\r', ' ').strip();
        return message.length() <= MAX_SAFE_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_SAFE_MESSAGE_LENGTH);
    }

    private String defaultMessage(ErrorCode code) {
        return switch (code) {
            case MEDIA_NOT_DECODABLE -> "Запись не удалось декодировать.";
            case NO_AUDIO_TRACK -> "В записи отсутствует аудиодорожка.";
            case DURATION_LIMIT_EXCEEDED -> "Длительность записи превышает установленный лимит.";
            default -> "Не удалось обработать запись в AI-сервисе.";
        };
    }
}
