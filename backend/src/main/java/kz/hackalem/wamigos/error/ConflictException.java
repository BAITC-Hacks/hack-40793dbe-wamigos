package kz.hackalem.wamigos.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException() {
        super(
                HttpStatus.CONFLICT,
                ErrorCode.EXPORT_NOT_READY,
                "Экспорт недоступен",
                "Дождитесь успешного завершения обработки."
        );
    }
}
