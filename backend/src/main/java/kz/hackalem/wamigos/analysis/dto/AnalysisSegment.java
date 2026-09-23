package kz.hackalem.wamigos.analysis.dto;

import java.util.List;
import kz.hackalem.wamigos.meeting.domain.SegmentTag;
import lombok.Builder;

@Builder
public record AnalysisSegment(
        String id,
        String speakerId,
        Long startMs,
        Long endMs,
        String text,
        List<SegmentTag> tags
) {
}
