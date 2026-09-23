package kz.hackalem.wamigos.analysis.port;

import kz.hackalem.wamigos.error.ErrorCode;
import lombok.Getter;

@Getter
public class MeetingAnalysisException extends RuntimeException {

    private final ErrorCode code;

    public MeetingAnalysisException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public MeetingAnalysisException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
