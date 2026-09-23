package kz.hackalem.wamigos.error;

import lombok.Builder;

@Builder
public record FieldErrorResponse(String field, String message) {
}
