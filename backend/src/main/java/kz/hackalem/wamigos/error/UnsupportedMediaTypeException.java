package kz.hackalem.wamigos.error;

import org.springframework.http.HttpStatus;

public class UnsupportedMediaTypeException extends ApiException {

    public UnsupportedMediaTypeException() {
        super(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "Неподдерживаемый формат",
                "Выберите поддерживаемую запись MP3, MP4, WebM или Ogg."
        );
    }
}
