package kz.hackalem.wamigos.meeting.validation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kz.hackalem.wamigos.analysis.port.MeetingAnalysisException;
import kz.hackalem.wamigos.config.UploadProperties;
import kz.hackalem.wamigos.error.ErrorCode;
import kz.hackalem.wamigos.meeting.dto.MeetingResultResponse;
import kz.hackalem.wamigos.meeting.dto.ProblemResponse;
import kz.hackalem.wamigos.meeting.dto.SegmentResponse;
import kz.hackalem.wamigos.meeting.dto.SpeakerResponse;
import kz.hackalem.wamigos.meeting.dto.TaskResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MeetingResultValidator {

    private final UploadProperties uploadProperties;

    public void validate(MeetingResultResponse result) {
        if (result == null || result.durationMs() == null || result.durationMs() <= 0) {
            invalid("Результат анализа не содержит корректную длительность.");
        }
        if (result.durationMs() > uploadProperties.maxDuration().toMillis()) {
            throw new MeetingAnalysisException(
                    ErrorCode.DURATION_LIMIT_EXCEEDED,
                    "Длительность записи превышает установленный лимит."
            );
        }
        requireNonBlank(result.summary(), "Результат анализа не содержит итогов.");
        requireLists(result);

        Set<String> globalIds = new HashSet<>();
        Set<String> speakerIds = validateSpeakers(result.speakers(), globalIds);
        Set<String> segmentIds = validateSegments(result.segments(), speakerIds, globalIds);
        validateTasks(result.tasks(), segmentIds, globalIds);
        validateProblems(result.problems(), segmentIds, globalIds);
    }

    private Set<String> validateSpeakers(List<SpeakerResponse> speakers, Set<String> globalIds) {
        Set<String> speakerIds = new HashSet<>();
        for (SpeakerResponse speaker : speakers) {
            requireNonBlank(speaker.id(), "Говорящий должен иметь идентификатор.");
            requireUnique(speaker.id(), speakerIds, globalIds);
        }
        return speakerIds;
    }

    private Set<String> validateSegments(
            List<SegmentResponse> segments,
            Set<String> speakerIds,
            Set<String> globalIds
    ) {
        Set<String> segmentIds = new HashSet<>();
        long previousStart = -1;
        for (SegmentResponse segment : segments) {
            requireNonBlank(segment.id(), "Реплика должна иметь идентификатор.");
            requireUnique(segment.id(), segmentIds, globalIds);
            if (segment.startMs() == null || segment.endMs() == null
                    || segment.startMs() < 0 || segment.endMs() <= segment.startMs()) {
                invalid("Реплика содержит некорректный временной интервал.");
            }
            if (segment.startMs() < previousStart) {
                invalid("Реплики должны быть упорядочены по времени.");
            }
            previousStart = segment.startMs();
            requireNonBlank(segment.text(), "Реплика не должна быть пустой.");
            if (segment.tags() == null) {
                invalid("Список меток реплики обязателен.");
            }
            if (segment.speakerId() != null && !speakerIds.contains(segment.speakerId())) {
                invalid("Реплика ссылается на неизвестного говорящего.");
            }
        }
        return segmentIds;
    }

    private void validateTasks(List<TaskResponse> tasks, Set<String> segmentIds, Set<String> globalIds) {
        Set<String> taskIds = new HashSet<>();
        for (TaskResponse task : tasks) {
            requireNonBlank(task.id(), "Поручение должно иметь идентификатор.");
            requireUnique(task.id(), taskIds, globalIds);
            requireNonBlank(task.text(), "Текст поручения не должен быть пустым.");
            validateSourceSegments(task.sourceSegmentIds(), segmentIds);
            if (task.deadlineDate() != null && isBlank(task.deadlineRaw())) {
                invalid("Уточнённая дата срока должна сопровождаться исходной формулировкой.");
            }
        }
    }

    private void validateProblems(
            List<ProblemResponse> problems,
            Set<String> segmentIds,
            Set<String> globalIds
    ) {
        Set<String> problemIds = new HashSet<>();
        for (ProblemResponse problem : problems) {
            requireNonBlank(problem.id(), "Проблема должна иметь идентификатор.");
            requireUnique(problem.id(), problemIds, globalIds);
            requireNonBlank(problem.text(), "Текст проблемы не должен быть пустым.");
            validateSourceSegments(problem.sourceSegmentIds(), segmentIds);
        }
    }

    private void validateSourceSegments(List<String> sourceIds, Set<String> segmentIds) {
        if (sourceIds == null) {
            invalid("Список исходных реплик обязателен.");
        }
        for (String sourceId : sourceIds) {
            if (!segmentIds.contains(sourceId)) {
                invalid("Результат ссылается на неизвестную реплику.");
            }
        }
    }

    private void requireLists(MeetingResultResponse result) {
        if (result.speakers() == null || result.segments() == null
                || result.tasks() == null || result.problems() == null) {
            invalid("Списки результата анализа обязательны.");
        }
    }

    private void requireUnique(String id, Set<String> localIds, Set<String> globalIds) {
        if (!localIds.add(id) || !globalIds.add(id)) {
            invalid("Идентификаторы результата анализа должны быть уникальны.");
        }
    }

    private void requireNonBlank(String value, String message) {
        if (isBlank(value)) {
            invalid(message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void invalid(String message) {
        throw new MeetingAnalysisException(ErrorCode.AI_PROCESSING_FAILED, message);
    }
}
