package kz.hackalem.wamigos.meeting.dto;

import lombok.Builder;

@Builder
public record SpeakerResponse(String id, String name) {
}
