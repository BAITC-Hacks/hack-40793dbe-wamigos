package kz.hackalem.wamigos.error;

import org.springframework.http.HttpStatus;

public class ExportException extends ApiException {

    public ExportException(Throwable cause) {
        super(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.EXPORT_FAILED,
                "Ошибка экспорта",
                "Не удалось сформировать документ."
        );
        initCause(cause);
    }
}
