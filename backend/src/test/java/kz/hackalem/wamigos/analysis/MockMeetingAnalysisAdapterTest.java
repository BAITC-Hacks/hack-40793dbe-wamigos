package kz.hackalem.wamigos.analysis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import kz.hackalem.wamigos.analysis.adapter.MockMeetingAnalysisAdapter;
import kz.hackalem.wamigos.analysis.dto.MeetingAnalysisRequest;
import kz.hackalem.wamigos.analysis.port.MeetingAnalysisException;
import kz.hackalem.wamigos.config.AiProperties;
import kz.hackalem.wamigos.error.ErrorCode;
import org.junit.jupiter.api.Test;

class MockMeetingAnalysisAdapterTest {

    @Test
    void forcedFailureUsesDocumentedCode() {
        MockMeetingAnalysisAdapter adapter = new MockMeetingAnalysisAdapter(
                new AiProperties("mock", Duration.ZERO, true)
        );

        assertThatThrownBy(() -> adapter.analyze(MeetingAnalysisRequest.builder().build()))
                .isInstanceOf(MeetingAnalysisException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.AI_PROCESSING_FAILED);
    }
}
