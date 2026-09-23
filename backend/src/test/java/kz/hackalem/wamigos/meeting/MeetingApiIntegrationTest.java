package kz.hackalem.wamigos.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import kz.hackalem.wamigos.PostgresIntegrationTest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest(properties = {
        "app.queue.poll-interval=PT0.05S",
        "app.ai.mock-delay=PT0.15S"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MeetingApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void completesProtectedHappyPathAndExportsBothFormats() throws Exception {
        JsonNode created = createMeeting("Совещание Ә Ғ Қ Ң Ө Ұ Ү Һ І");
        String id = created.get("id").asText();
        String token = created.get("accessToken").asText();

        mockMvc.perform(get("/api/v1/meetings/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_AVAILABLE"));

        JsonNode completed = waitForTerminal(id, token, Duration.ofSeconds(10));
        assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.at("/result/speakers").size()).isGreaterThanOrEqualTo(1);
        assertThat(completed.at("/result/segments").size()).isGreaterThanOrEqualTo(2);
        assertThat(completed.at("/result/tasks/0/sourceSegmentIds/0").asText()).isEqualTo("seg1");

        MvcResult pdf = mockMvc.perform(get("/api/v1/meetings/{id}/export", id)
                        .param("format", "pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andReturn();
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 4, StandardCharsets.US_ASCII))
                .isEqualTo("%PDF");
        try (PDDocument document = Loader.loadPDF(pdf.getResponse().getContentAsByteArray())) {
            assertThat(new PDFTextStripper().getText(document)).contains("Ә Ғ Қ Ң Ө Ұ Ү Һ І");
        }

        MvcResult docx = mockMvc.perform(get("/api/v1/meetings/{id}/export", id)
                        .param("format", "docx")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                ))
                .andReturn();
        assertThat(docx.getResponse().getContentAsByteArray()).startsWith((byte) 'P', (byte) 'K');
        try (XWPFDocument document = new XWPFDocument(
                new ByteArrayInputStream(docx.getResponse().getContentAsByteArray())
        ); XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            assertThat(extractor.getText()).contains("Ә Ғ Қ Ң Ө Ұ Ү Һ І");
        }
    }

    @Test
    void rejectsUnsupportedContentAndMalformedIdWithProblemJson() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "not audio".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/v1/meetings")
                        .file(file)
                        .param("source", "UPLOAD"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

        mockMvc.perform(get("/api/v1/meetings/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(multipart("/api/v1/meetings")
                        .file(mp4File())
                        .param("source", "UPLOAD"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    private JsonNode createMeeting(String title) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "meeting.mp3",
                "audio/mpeg",
                mp3Bytes()
        );
        MvcResult result = mockMvc.perform(multipart("/api/v1/meetings")
                        .file(file)
                        .param("source", "UPLOAD")
                        .param("title", title)
                        .param("startedAt", "2026-09-23T10:00:00+06:00")
                        .param("timeZone", "Asia/Almaty"))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.expiresAt").doesNotExist())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode waitForTerminal(String id, String token, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/v1/meetings/{id}", id)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andReturn();
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
            if (body.get("status").asText().matches("COMPLETED|FAILED")) {
                return body;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Meeting did not reach a terminal state");
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

    private MockMultipartFile mp4File() {
        byte[] bytes = new byte[32];
        bytes[3] = 24;
        bytes[4] = 'f';
        bytes[5] = 't';
        bytes[6] = 'y';
        bytes[7] = 'p';
        bytes[8] = 'i';
        bytes[9] = 's';
        bytes[10] = 'o';
        bytes[11] = 'm';
        bytes[16] = 'i';
        bytes[17] = 's';
        bytes[18] = 'o';
        bytes[19] = 'm';
        bytes[20] = 'm';
        bytes[21] = 'p';
        bytes[22] = '4';
        bytes[23] = '2';
        return new MockMultipartFile("file", "meeting.mp4", "video/mp4", bytes);
    }
}
