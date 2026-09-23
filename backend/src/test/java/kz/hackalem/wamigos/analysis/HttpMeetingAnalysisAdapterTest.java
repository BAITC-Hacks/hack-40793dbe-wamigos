package kz.hackalem.wamigos.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import kz.hackalem.wamigos.TestMediaFixtures;
import kz.hackalem.wamigos.analysis.adapter.HttpMeetingAnalysisAdapter;
import kz.hackalem.wamigos.analysis.dto.MeetingAnalysisOutput;
import kz.hackalem.wamigos.analysis.dto.MeetingAnalysisRequest;
import kz.hackalem.wamigos.analysis.health.AiServiceHealthIndicator;
import kz.hackalem.wamigos.analysis.mapper.AiAnalysisResponseMapper;
import kz.hackalem.wamigos.analysis.mapper.AiHttpErrorMapper;
import kz.hackalem.wamigos.analysis.port.MeetingAnalysisException;
import kz.hackalem.wamigos.config.AiClientConfiguration;
import kz.hackalem.wamigos.config.AiProperties;
import kz.hackalem.wamigos.config.StorageProperties;
import kz.hackalem.wamigos.error.ErrorCode;
import kz.hackalem.wamigos.storage.adapter.LocalStorageAdapter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class HttpMeetingAnalysisAdapterTest {

    @TempDir
    private Path storageRoot;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockWebServer server;
    private boolean serverRunning;
    private LocalStorageAdapter storage;
    private String storageKey;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        serverRunning = true;
        storage = new LocalStorageAdapter(new StorageProperties(storageRoot));
        storage.initialize();
        storageKey = storage.store(new ByteArrayInputStream(TestMediaFixtures.mp3()), ".mp3");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (serverRunning) {
            server.shutdown();
        }
    }

    @Test
    void streamsMultipartAndMapsSuccessfulResponse() throws Exception {
        server.enqueue(jsonResponse(200, AiTestResponses.valid()));

        MeetingAnalysisOutput output = adapter(server.url("/").uri(), Duration.ofSeconds(2))
                .analyze(request(
                        Instant.parse("2026-09-23T04:30:00Z"),
                        "Asia/Almaty"
                ));

        assertThat(output.durationMs()).isEqualTo(12_000L);
        assertThat(output.segments()).hasSize(2);
        assertThat(output.tasks()).singleElement().satisfies(task ->
                assertThat(task.sourceSegmentIds()).containsExactly("seg1")
        );

        RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getPath()).isEqualTo("/internal/v1/analyze");
        MediaType requestContentType = MediaType.parseMediaType(recorded.getHeader("Content-Type"));
        assertThat(requestContentType.getType()).isEqualTo("multipart");
        assertThat(requestContentType.getSubtype()).isEqualTo("form-data");
        assertThat(requestContentType.getParameter("boundary")).isNotBlank();
        String multipart = recorded.getBody().readUtf8();
        assertThat(multipart)
                .contains("name=\"file\"")
                .contains("filename=\"")
                .contains("Content-Type: audio/mpeg")
                .contains("name=\"startedAt\"")
                .contains("2026-09-23T09:30+05:00")
                .contains("name=\"timeZone\"")
                .contains("Asia/Almaty")
                .doesNotContain("jobId")
                .doesNotContain("accessToken")
                .doesNotContain("participants")
                .doesNotContain("diagnostics");
    }

    @Test
    void omitsOptionalMetadataWithoutInventingUploadTimestamp() throws Exception {
        server.enqueue(jsonResponse(200, AiTestResponses.valid()));

        adapter(server.url("/").uri(), Duration.ofSeconds(2)).analyze(request(null, null));

        RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getBody().readUtf8())
                .doesNotContain("name=\"startedAt\"")
                .doesNotContain("name=\"timeZone\"")
                .doesNotContain("createdAt");
    }

    @ParameterizedTest
    @CsvSource({
            "MEDIA_NOT_DECODABLE",
            "NO_AUDIO_TRACK",
            "DURATION_LIMIT_EXCEEDED"
    })
    void preservesDocumentedMediaErrorCodes(String code) {
        server.enqueue(jsonResponse(422, "{\"code\":\"" + code + "\",\"message\":\"safe message\"}"));

        assertThatThrownBy(() -> adapter(server.url("/").uri(), Duration.ofSeconds(2))
                .analyze(request(null, null)))
                .isInstanceOf(MeetingAnalysisException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.valueOf(code));
    }

    @ParameterizedTest
    @CsvSource({"500", "503"})
    void mapsServerFailuresToAiProcessingFailed(int status) {
        server.enqueue(jsonResponse(status, "{\"code\":\"INTERNAL_MODEL_ERROR\",\"message\":\"details\"}"));

        assertThatThrownBy(() -> adapter(server.url("/").uri(), Duration.ofSeconds(2))
                .analyze(request(null, null)))
                .isInstanceOf(MeetingAnalysisException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.AI_PROCESSING_FAILED);
    }

    @Test
    void mapsMalformedSuccessJsonToAiProcessingFailed() {
        server.enqueue(jsonResponse(200, "{not-json"));

        assertThatThrownBy(() -> adapter(server.url("/").uri(), Duration.ofSeconds(2))
                .analyze(request(null, null)))
                .isInstanceOf(MeetingAnalysisException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.AI_PROCESSING_FAILED);
    }

    @Test
    void mapsConnectionRefusedToAiProcessingFailed() throws IOException {
        URI unavailableUrl = server.url("/").uri();
        server.shutdown();
        serverRunning = false;

        assertThatThrownBy(() -> adapter(unavailableUrl, Duration.ofMillis(200)).analyze(request(null, null)))
                .isInstanceOf(MeetingAnalysisException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.AI_PROCESSING_FAILED);
    }

    @Test
    void mapsResponseTimeoutToAiProcessingFailed() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        assertThatThrownBy(() -> adapter(server.url("/").uri(), Duration.ofMillis(100))
                .analyze(request(null, null)))
                .isInstanceOf(MeetingAnalysisException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.AI_PROCESSING_FAILED);
    }

    @Test
    void reportsAiHealthWithoutCallingAnalyze() {
        server.enqueue(new MockResponse().setResponseCode(200));
        AiProperties properties = properties(server.url("/").uri(), Duration.ofSeconds(1));
        RestClient healthClient = new AiClientConfiguration().aiHealthRestClient(properties);
        AiServiceHealthIndicator indicator = new AiServiceHealthIndicator(healthClient);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsAiHealthDownOnServiceFailure() {
        server.enqueue(new MockResponse().setResponseCode(503));
        AiProperties properties = properties(server.url("/").uri(), Duration.ofSeconds(1));
        RestClient healthClient = new AiClientConfiguration().aiHealthRestClient(properties);
        AiServiceHealthIndicator indicator = new AiServiceHealthIndicator(healthClient);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    private HttpMeetingAnalysisAdapter adapter(URI baseUrl, Duration responseTimeout) {
        AiProperties properties = properties(baseUrl, responseTimeout);
        RestClient client = new AiClientConfiguration().aiAnalysisRestClient(properties);
        return new HttpMeetingAnalysisAdapter(
                client,
                storage,
                new AiAnalysisResponseMapper(),
                new AiHttpErrorMapper(),
                objectMapper
        );
    }

    private AiProperties properties(URI baseUrl, Duration responseTimeout) {
        return new AiProperties(baseUrl, Duration.ofMillis(200), responseTimeout, Duration.ofMillis(200));
    }

    private MeetingAnalysisRequest request(Instant startedAt, String timeZone) {
        return MeetingAnalysisRequest.builder()
                .meetingId(java.util.UUID.randomUUID())
                .storageKey(storageKey)
                .contentType("audio/mpeg")
                .startedAt(startedAt)
                .timeZone(timeZone)
                .build();
    }

    private MockResponse jsonResponse(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(body);
    }
}
