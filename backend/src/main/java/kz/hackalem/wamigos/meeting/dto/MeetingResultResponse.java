package kz.hackalem.wamigos.meeting.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record MeetingResultResponse(
        Long durationMs,
        String summary,
        List<SpeakerResponse> speakers,
        List<SegmentResponse> segments,
        List<TaskResponse> tasks,
        List<ProblemResponse> problems
) {
}
