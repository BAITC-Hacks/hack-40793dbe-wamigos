package kz.hackalem.wamigos.error;

import org.springframework.http.HttpStatus;

public class StorageException extends ApiException {

    public StorageException(String detail) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.STORAGE_ERROR, "Ошибка хранилища", detail);
    }

    public StorageException(String detail, Throwable cause) {
        this(detail);
        initCause(cause);
    }
}
