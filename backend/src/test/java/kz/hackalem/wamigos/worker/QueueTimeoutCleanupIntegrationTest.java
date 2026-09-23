package kz.hackalem.wamigos.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import kz.hackalem.wamigos.PostgresIntegrationTest;
import kz.hackalem.wamigos.meeting.dto.MeetingJobDto;
import kz.hackalem.wamigos.meeting.service.MeetingJobService;
import kz.hackalem.wamigos.storage.port.StoragePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "app.processing.worker-enabled=false",
        "app.queue.capacity=1",
        "app.processing.timeout=PT30M"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QueueTimeoutCleanupIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeetingJobService meetingJobService;

    @Autowired
    private MeetingMaintenanceWorker meetingMaintenanceWorker;

    @Autowired
    private StoragePort storagePort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void enforcesQueueCapacityTimesOutProcessingAndCleansExpiredData() throws Exception {
        JsonNode created = createMeeting();
        UUID id = UUID.fromString(created.get("id").asText());
        String token = created.get("accessToken").asText();

        mockMvc.perform(get("/api/v1/meetings/{id}/export", id)
                        .param("format", "pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXPORT_NOT_READY"));

        mockMvc.perform(multipart("/api/v1/meetings")
                        .file(mp3File())
                        .param("source", "UPLOAD"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("QUEUE_FULL"));

        MeetingJobDto claimed = meetingJobService
                .claimNextQueued(Instant.now().minusSeconds(31 * 60))
                .orElseThrow();
        assertThat(claimed.id()).isEqualTo(id);
        assertThat(meetingJobService.claimNextQueued(Instant.now())).isEmpty();
        Path sourcePath = storagePort.load(claimed.sourcePath()).getFile().toPath();
        assertThat(sourcePath).exists();

        meetingMaintenanceWorker.failTimedOutJobs();
        mockMvc.perform(get("/api/v1/meetings/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error.code").value("PROCESSING_TIMEOUT"));

        jdbcTemplate.update(
                "UPDATE meeting_jobs SET expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
                id
        );
        mockMvc.perform(get("/api/v1/meetings/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_AVAILABLE"));

        meetingMaintenanceWorker.cleanupExpiredJobs();
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM meeting_jobs WHERE id = ?",
                Integer.class,
                id
        );
        assertThat(rows).isZero();
        assertThat(sourcePath).doesNotExist();

        JsonNode interruptedCreated = createMeeting();
        UUID interruptedId = UUID.fromString(interruptedCreated.get("id").asText());
        String interruptedToken = interruptedCreated.get("accessToken").asText();
        meetingJobService.claimNextQueued(Instant.now()).orElseThrow();
        Instant interruptedAt = Instant.now();
        assertThat(meetingJobService.recoverInterrupted(
                interruptedAt,
                interruptedAt.plus(Duration.ofHours(24))
        )).isEqualTo(1);
        mockMvc.perform(get("/api/v1/meetings/{id}", interruptedId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + interruptedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error.code").value("PROCESSING_INTERRUPTED"));
    }

    private JsonNode createMeeting() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/meetings")
                        .file(mp3File())
                        .param("source", "UPLOAD"))
                .andExpect(status().isAccepted())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private MockMultipartFile mp3File() {
        byte[] bytes = new byte[1_024];
        bytes[0] = 'I';
        bytes[1] = 'D';
        bytes[2] = '3';
        bytes[3] = 4;
        bytes[10] = (byte) 0xff;
        bytes[11] = (byte) 0xfb;
        return new MockMultipartFile("file", "meeting.mp3", "audio/mpeg", bytes);
    }
}
