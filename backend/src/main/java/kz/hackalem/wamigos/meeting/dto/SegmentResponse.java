package kz.hackalem.wamigos.meeting.dto;

import java.util.List;
import kz.hackalem.wamigos.meeting.domain.SegmentTag;
import lombok.Builder;

@Builder
public record SegmentResponse(
        String id,
        String speakerId,
        Long startMs,
        Long endMs,
        String text,
        List<SegmentTag> tags
) {
}
