package kz.hackalem.wamigos.analysis.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import kz.hackalem.wamigos.analysis.dto.MeetingAnalysisOutput;
import kz.hackalem.wamigos.analysis.dto.MeetingAnalysisRequest;
import kz.hackalem.wamigos.analysis.mapper.AiAnalysisResponseMapper;
import kz.hackalem.wamigos.analysis.mapper.AiHttpErrorMapper;
import kz.hackalem.wamigos.analysis.port.MeetingAnalysisException;
import kz.hackalem.wamigos.analysis.port.MeetingAnalysisPort;
import kz.hackalem.wamigos.analysis.transport.AiAnalysisResponse;
import kz.hackalem.wamigos.analysis.transport.AiErrorResponse;
import kz.hackalem.wamigos.error.ErrorCode;
import kz.hackalem.wamigos.storage.port.StoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class HttpMeetingAnalysisAdapter implements MeetingAnalysisPort {

    private static final String ANALYZE_PATH = "/internal/v1/analyze";

    private final RestClient restClient;
    private final StoragePort storagePort;
    private final AiAnalysisResponseMapper responseMapper;
    private final AiHttpErrorMapper errorMapper;
    private final ObjectMapper objectMapper;

    public HttpMeetingAnalysisAdapter(
            @Qualifier("aiAnalysisRestClient") RestClient restClient,
            StoragePort storagePort,
            AiAnalysisResponseMapper responseMapper,
            AiHttpErrorMapper errorMapper,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.storagePort = storagePort;
        this.responseMapper = responseMapper;
        this.errorMapper = errorMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public MeetingAnalysisOutput analyze(MeetingAnalysisRequest request) {
        long startedNanos = System.nanoTime();
        try {
            AiAnalysisResponse response = restClient.post()
                    .uri(ANALYZE_PATH)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(createMultipart(request))
                    .retrieve()
                    .body(AiAnalysisResponse.class);
            MeetingAnalysisOutput output = responseMapper.toOutput(response);
            log.info(
                    "AI analysis completed for job {} in {} ms with {} speakers, {} segments, {} tasks and {} problems",
                    request.meetingId(),
                    elapsedMillis(startedNanos),
                    size(output.speakers()),
                    size(output.segments()),
                    size(output.tasks()),
                    size(output.problems())
            );
            return output;
        } catch (RestClientResponseException exception) {
            AiErrorResponse errorResponse = readErrorResponse(exception);
            MeetingAnalysisException mapped = errorMapper.toException(exception.getStatusCode(), errorResponse);
            log.warn(
                    "AI analysis rejected for job {} with HTTP {} and code {}",
                    request.meetingId(),
                    exception.getStatusCode().value(),
                    mapped.getCode()
            );
            throw mapped;
        } catch (ResourceAccessException exception) {
            log.warn("AI analysis connection failed for job {}", request.meetingId());
            throw new MeetingAnalysisException(
                    ErrorCode.AI_PROCESSING_FAILED,
                    "AI-сервис недоступен или превысил время ожидания.",
                    exception
            );
        } catch (RestClientException exception) {
            log.warn("AI analysis response could not be read for job {}", request.meetingId());
            throw new MeetingAnalysisException(
                    ErrorCode.AI_PROCESSING_FAILED,
                    "AI-сервис вернул некорректный ответ.",
                    exception
            );
        }
    }

    private MultiValueMap<String, Object> createMultipart(MeetingAnalysisRequest request) {
        Resource media = storagePort.load(request.storageKey());
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(request.contentType()));
        fileHeaders.setContentDisposition(ContentDisposition.formData()
                .name("file")
                .filename(Objects.requireNonNullElse(media.getFilename(), "meeting-media"))
                .build());
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(media, fileHeaders));
        if (request.startedAt() != null) {
            body.add("startedAt", formatStartedAt(request.startedAt(), request.timeZone()));
        }
        if (isNamedTimeZone(request.timeZone())) {
            body.add("timeZone", request.timeZone());
        }
        return body;
    }

    private String formatStartedAt(Instant startedAt, String timeZone) {
        ZoneId zone = timeZone == null || timeZone.isBlank() ? ZoneOffset.UTC : ZoneId.of(timeZone);
        return startedAt.atZone(zone).toOffsetDateTime().toString();
    }

    private boolean isNamedTimeZone(String timeZone) {
        return timeZone != null && !timeZone.isBlank() && !(ZoneId.of(timeZone) instanceof ZoneOffset);
    }

    private AiErrorResponse readErrorResponse(RestClientResponseException exception) {
        try {
            return objectMapper.readValue(exception.getResponseBodyAsByteArray(), AiErrorResponse.class);
        } catch (IOException parseException) {
            log.debug(
                    "AI error response for HTTP {} was not valid JSON: {}",
                    exception.getStatusCode().value(),
                    parseException.getClass().getSimpleName()
            );
            return null;
        }
    }

    private long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    private int size(java.util.Collection<?> values) {
        return values == null ? 0 : values.size();
    }
}
