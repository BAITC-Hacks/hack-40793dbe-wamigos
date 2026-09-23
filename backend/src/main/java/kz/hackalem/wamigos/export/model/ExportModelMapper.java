package kz.hackalem.wamigos.export.model;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;
import kz.hackalem.wamigos.meeting.dto.MeetingJobDto;
import kz.hackalem.wamigos.meeting.dto.SpeakerResponse;
import kz.hackalem.wamigos.meeting.dto.TaskResponse;
import org.springframework.stereotype.Component;

@Component
public class ExportModelMapper {

    private static final String NOT_SPECIFIED = "Не указан";
    private static final DateTimeFormatter MEETING_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DEADLINE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public ExportModel toModel(MeetingJobDto job) {
        Map<String, String> speakerNames = job.result().speakers().stream()
                .collect(Collectors.toUnmodifiableMap(
                        SpeakerResponse::id,
                        speaker -> valueOrDefault(speaker.name())
                ));
        return ExportModel.builder()
                .title(job.title())
                .meetingDate(formatMeetingDate(job))
                .speakers(job.result().speakers().stream()
                        .map(SpeakerResponse::name)
                        .map(this::valueOrDefault)
                        .toList())
                .summary(job.result().summary())
                .tasks(job.result().tasks().stream().map(this::toTask).toList())
                .problems(job.result().problems().stream()
                        .map(problem -> ExportProblem.builder()
                                .text(problem.text())
                                .reportedBy(valueOrDefault(problem.reportedBy()))
                                .build())
                        .toList())
                .transcript(job.result().segments().stream()
                        .map(segment -> ExportTranscriptLine.builder()
                                .timeCode(formatTimeCode(segment.startMs()))
                                .speaker(segment.speakerId() == null
                                        ? NOT_SPECIFIED
                                        : speakerNames.getOrDefault(segment.speakerId(), NOT_SPECIFIED))
                                .text(segment.text())
                                .build())
                        .toList())
                .build();
    }

    private ExportTask toTask(TaskResponse task) {
        String deadline = valueOrDefault(task.deadlineRaw());
        if (task.deadlineDate() != null) {
            deadline = deadline + " (" + DEADLINE_DATE_FORMAT.format(task.deadlineDate()) + ")";
        }
        return ExportTask.builder()
                .action(task.text())
                .assignee(valueOrDefault(task.assigneeName()))
                .assigner(valueOrDefault(task.assignerName()))
                .deadline(deadline)
                .build();
    }

    private String formatMeetingDate(MeetingJobDto job) {
        if (job.startedAt() == null) {
            return NOT_SPECIFIED;
        }
        ZoneId zoneId = job.timeZone() == null ? ZoneId.of("UTC") : ZoneId.of(job.timeZone());
        return MEETING_DATE_FORMAT.format(job.startedAt().atZone(zoneId));
    }

    private String formatTimeCode(Long milliseconds) {
        long totalSeconds = milliseconds / 1_000;
        long hours = totalSeconds / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format("%02d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }

    private String valueOrDefault(String value) {
        return value == null || value.isBlank() ? NOT_SPECIFIED : value;
    }
}
