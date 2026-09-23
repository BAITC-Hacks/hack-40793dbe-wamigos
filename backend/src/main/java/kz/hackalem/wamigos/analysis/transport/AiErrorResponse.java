package kz.hackalem.wamigos.analysis.transport;

import lombok.Builder;

@Builder
public record AiErrorResponse(String code, String message) {
}
