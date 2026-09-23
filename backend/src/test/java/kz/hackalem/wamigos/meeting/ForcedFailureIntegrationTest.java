package kz.hackalem.wamigos.meeting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import kz.hackalem.wamigos.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "app.queue.poll-interval=PT0.05S",
        "app.ai.mock-delay=PT0.01S",
        "app.ai.mock-force-failure=true"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ForcedFailureIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void forcedMockFailurePersistsFailedState() throws Exception {
        MvcResult createdResult = mockMvc.perform(multipart("/api/v1/meetings")
                        .file(new MockMultipartFile("file", "meeting.mp3", "audio/mpeg", mp3Bytes()))
                        .param("source", "UPLOAD"))
                .andExpect(status().isAccepted())
                .andReturn();
        JsonNode created = objectMapper.readTree(createdResult.getResponse().getContentAsByteArray());

        JsonNode failed = waitForFailure(
                created.get("id").asText(),
                created.get("accessToken").asText(),
                Duration.ofSeconds(10)
        );
        org.assertj.core.api.Assertions.assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        org.assertj.core.api.Assertions.assertThat(failed.at("/error/code").asText())
                .isEqualTo("AI_PROCESSING_FAILED");
    }

    private JsonNode waitForFailure(String id, String token, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/v1/meetings/{id}", id)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
            if (body.get("status").asText().equals("FAILED")) {
                return body;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Meeting did not reach FAILED state");
    }

    private byte[] mp3Bytes() {
        byte[] bytes = new byte[1_024];
        bytes[0] = 'I';
        bytes[1] = 'D';
        bytes[2] = '3';
        bytes[3] = 4;
        bytes[10] = (byte) 0xff;
        bytes[11] = (byte) 0xfb;
        return bytes;
    }
}
