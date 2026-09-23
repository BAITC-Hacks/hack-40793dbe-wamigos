package kz.hackalem.wamigos.error;

import org.springframework.http.HttpStatus;

public class ValidationException extends ApiException {

    public ValidationException(String detail) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "Некорректный запрос", detail);
    }
}
