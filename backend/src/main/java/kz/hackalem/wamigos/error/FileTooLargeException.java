package kz.hackalem.wamigos.error;

import org.springframework.http.HttpStatus;

public class FileTooLargeException extends ApiException {

    public FileTooLargeException() {
        super(
                HttpStatus.PAYLOAD_TOO_LARGE,
                ErrorCode.FILE_TOO_LARGE,
                "Файл слишком большой",
                "Размер файла превышает установленный лимит."
        );
    }
}
