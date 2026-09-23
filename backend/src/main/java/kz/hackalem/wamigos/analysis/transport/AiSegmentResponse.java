package kz.hackalem.wamigos.analysis.transport;

import java.util.List;
import lombok.Builder;

@Builder
public record AiSegmentResponse(
        String id,
        String speakerId,
        Long startMs,
        Long endMs,
        String text,
        List<String> tags
) {
}
