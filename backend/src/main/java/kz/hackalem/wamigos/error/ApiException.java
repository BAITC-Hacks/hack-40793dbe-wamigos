package kz.hackalem.wamigos.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    private final String title;

    protected ApiException(HttpStatus status, ErrorCode code, String title, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
        this.title = title;
    }
}
