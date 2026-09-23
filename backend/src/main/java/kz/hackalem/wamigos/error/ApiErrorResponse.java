package kz.hackalem.wamigos.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;

@Builder
public record ApiErrorResponse(
        String type,
        String title,
        Integer status,
        String detail,
        ErrorCode code,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<FieldErrorResponse> errors
) {
}
