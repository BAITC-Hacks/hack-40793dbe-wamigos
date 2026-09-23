package kz.hackalem.wamigos.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException() {
        super(
                HttpStatus.NOT_FOUND,
                ErrorCode.MEETING_NOT_AVAILABLE,
                "Запись недоступна",
                "Запись не найдена, недоступна или срок хранения истёк."
        );
    }
}
