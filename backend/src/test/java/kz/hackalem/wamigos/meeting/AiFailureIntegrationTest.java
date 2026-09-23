package kz.hackalem.wamigos.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import kz.hackalem.wamigos.PostgresIntegrationTest;
import kz.hackalem.wamigos.TestMediaFixtures;
import kz.hackalem.wamigos.analysis.AiTestResponses;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "app.queue.poll-interval=PT0.05S"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AiFailureIntegrationTest extends PostgresIntegrationTest {

    private static final MockWebServer AI_SERVER = startAiServer();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerAiProperties(DynamicPropertyRegistry registry) {
        registry.add("app.ai.base-url", () -> AI_SERVER.url("/").toString());
        registry.add("app.ai.connect-timeout", () -> "PT0.2S");
        registry.add("app.ai.response-timeout", () -> "PT0.2S");
        registry.add("app.ai.health-timeout", () -> "PT0.2S");
    }

    @AfterAll
    static void stopAiServer() throws IOException {
        AI_SERVER.shutdown();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "MEDIA_NOT_DECODABLE",
            "NO_AUDIO_TRACK",
            "DURATION_LIMIT_EXCEEDED"
    })
    void preservesDocumentedMediaFailure(String code) throws Exception {
        AI_SERVER.enqueue(jsonResponse(422, "{\"code\":\"" + code + "\",\"message\":\"safe message\"}"));

        assertJobFailsWith(code);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503})
    void mapsAiServiceFailureToGenericCode(int statusCode) throws Exception {
        AI_SERVER.enqueue(jsonResponse(
                statusCode,
                "{\"code\":\"INTERNAL_MODEL_ERROR\",\"message\":\"private details\"}"
        ));

        assertJobFailsWith("AI_PROCESSING_FAILED");
    }

    @Test
    void rejectsMalformedSuccessResponse() throws Exception {
        AI_SERVER.enqueue(jsonResponse(200, "{not-json"));

        assertJobFailsWith("AI_PROCESSING_FAILED");
    }

    @Test
    void rejectsInvalidSpeakerReference() throws Exception {
        AI_SERVER.enqueue(jsonResponse(200, AiTestResponses.invalidSpeakerReference()));

        assertJobFailsWith("AI_PROCESSING_FAILED");
    }

    @Test
    void rejectsBrokenSourceSegmentReference() throws Exception {
        AI_SERVER.enqueue(jsonResponse(200, AiTestResponses.brokenSourceReference()));

        assertJobFailsWith("AI_PROCESSING_FAILED");
    }

    @Test
    void mapsDisconnectedAiServiceToGenericCode() throws Exception {
        AI_SERVER.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        assertJobFailsWith("AI_PROCESSING_FAILED");
    }

    @Test
    void mapsAiResponseTimeoutToGenericCode() throws Exception {
        AI_SERVER.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        assertJobFailsWith("AI_PROCESSING_FAILED");
    }

    private void assertJobFailsWith(String expectedCode) throws Exception {
        JsonNode created = createMeeting();
        JsonNode failed = waitForTerminal(
                created.get("id").asText(),
                created.get("accessToken").asText(),
                Duration.ofSeconds(10)
        );

        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        assertThat(failed.at("/error/code").asText()).isEqualTo(expectedCode);
        assertThat(failed.hasNonNull("result")).isFalse();
    }

    private JsonNode createMeeting() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/meetings")
                        .file(new MockMultipartFile(
                                "file",
                                "meeting.mp3",
                                "audio/mpeg",
                                TestMediaFixtures.mp3()
                        ))
                        .param("source", "UPLOAD"))
                .andExpect(status().isAccepted())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode waitForTerminal(String id, String token, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/v1/meetings/{id}", id)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
            if (body.get("status").asText().matches("COMPLETED|FAILED")) {
                return body;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Meeting did not reach a terminal state");
    }

    private static MockWebServer startAiServer() {
        MockWebServer server = new MockWebServer();
        try {
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private MockResponse jsonResponse(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(body);
    }
}
