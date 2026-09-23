package kz.hackalem.wamigos.analysis.port;

import kz.hackalem.wamigos.analysis.dto.MeetingAnalysisOutput;
import kz.hackalem.wamigos.analysis.dto.MeetingAnalysisRequest;

public interface MeetingAnalysisPort {

    MeetingAnalysisOutput analyze(MeetingAnalysisRequest request);
}
