package kz.hackalem.wamigos.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import kz.hackalem.wamigos.PostgresIntegrationTest;
import kz.hackalem.wamigos.TestMediaFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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
class AiConnectionRefusedIntegrationTest extends PostgresIntegrationTest {

    private static final int UNUSED_PORT = findUnusedPort();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerAiProperties(DynamicPropertyRegistry registry) {
        registry.add("app.ai.base-url", () -> "http://127.0.0.1:" + UNUSED_PORT);
        registry.add("app.ai.connect-timeout", () -> "PT0.2S");
        registry.add("app.ai.response-timeout", () -> "PT0.2S");
        registry.add("app.ai.health-timeout", () -> "PT0.2S");
    }

    @Test
    void connectionRefusalDoesNotLeaveJobProcessing() throws Exception {
        MvcResult createdResult = mockMvc.perform(multipart("/api/v1/meetings")
                        .file(new MockMultipartFile(
                                "file",
                                "meeting.mp3",
                                "audio/mpeg",
                                TestMediaFixtures.mp3()
                        ))
                        .param("source", "UPLOAD"))
                .andExpect(status().isAccepted())
                .andReturn();
        JsonNode created = objectMapper.readTree(createdResult.getResponse().getContentAsByteArray());

        JsonNode failed = waitForTerminal(
                created.get("id").asText(),
                created.get("accessToken").asText(),
                Duration.ofSeconds(10)
        );
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        assertThat(failed.at("/error/code").asText()).isEqualTo("AI_PROCESSING_FAILED");
        assertThat(failed.hasNonNull("result")).isFalse();
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

    private static int findUnusedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
