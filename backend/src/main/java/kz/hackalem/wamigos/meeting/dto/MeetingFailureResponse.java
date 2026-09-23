package kz.hackalem.wamigos.meeting.dto;

import kz.hackalem.wamigos.error.ErrorCode;
import lombok.Builder;

@Builder
public record MeetingFailureResponse(ErrorCode code, String message) {
}
