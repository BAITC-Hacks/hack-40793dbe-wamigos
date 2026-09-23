package kz.hackalem.wamigos.meeting;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import kz.hackalem.wamigos.config.QueueProperties;
import kz.hackalem.wamigos.meeting.dto.CreateMeetingJobRequest;
import kz.hackalem.wamigos.meeting.dto.CreateMeetingRequest;
import kz.hackalem.wamigos.meeting.processor.MeetingProcessor;
import kz.hackalem.wamigos.meeting.service.AccessTokenManager;
import kz.hackalem.wamigos.meeting.service.MeetingJobService;
import kz.hackalem.wamigos.meeting.validation.MediaTypeDetector;
import kz.hackalem.wamigos.meeting.validation.MeetingRequestValidator;
import kz.hackalem.wamigos.storage.port.StoragePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class MeetingProcessorTest {

    @Mock
    private MeetingJobService meetingJobService;

    @Mock
    private StoragePort storagePort;

    @Mock
    private AccessTokenManager accessTokenManager;

    @Mock
    private MediaTypeDetector mediaTypeDetector;

    @Mock
    private MeetingRequestValidator meetingRequestValidator;

    @Mock
    private QueueProperties queueProperties;

    @Mock
    private Clock clock;

    @InjectMocks
    private MeetingProcessor meetingProcessor;

    @Test
    void deletesStoredFileWhenDatabaseJobCreationFails() {
        CreateMeetingRequest request = CreateMeetingRequest.builder()
                .file(new MockMultipartFile(
                        "file",
                        "meeting.mp3",
                        "audio/mpeg",
                        "ID3-content".getBytes(StandardCharsets.UTF_8)
                ))
                .build();
        when(mediaTypeDetector.detect(request.getFile())).thenReturn("audio/mpeg");
        when(queueProperties.capacity()).thenReturn(1);
        when(meetingJobService.hasQueueCapacity(1)).thenReturn(true);
        when(meetingRequestValidator.extensionFor("audio/mpeg")).thenReturn(".mp3");
        when(storagePort.store(any(InputStream.class), eq(".mp3"))).thenReturn("stored-file.mp3");
        when(accessTokenManager.generate()).thenReturn("token");
        when(accessTokenManager.hash("token")).thenReturn("hash");
        when(meetingJobService.create(any(CreateMeetingJobRequest.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> meetingProcessor.create(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(storagePort).delete("stored-file.mp3");
    }
}
