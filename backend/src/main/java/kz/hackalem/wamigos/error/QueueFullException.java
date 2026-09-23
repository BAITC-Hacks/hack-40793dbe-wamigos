package kz.hackalem.wamigos.error;

import org.springframework.http.HttpStatus;

public class QueueFullException extends ApiException {

    public QueueFullException() {
        super(
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.QUEUE_FULL,
                "Очередь заполнена",
                "Сервис временно не принимает новые записи. Повторите попытку позже."
        );
    }
}
