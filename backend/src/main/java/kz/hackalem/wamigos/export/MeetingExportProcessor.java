package kz.hackalem.wamigos.export;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kz.hackalem.wamigos.error.ConflictException;
import kz.hackalem.wamigos.export.model.ExportFormat;
import kz.hackalem.wamigos.export.model.ExportModel;
import kz.hackalem.wamigos.export.model.ExportModelMapper;
import kz.hackalem.wamigos.export.model.ExportedDocument;
import kz.hackalem.wamigos.export.renderer.ExportRenderer;
import kz.hackalem.wamigos.meeting.domain.MeetingStatus;
import kz.hackalem.wamigos.meeting.dto.MeetingJobDto;
import kz.hackalem.wamigos.meeting.service.AccessTokenManager;
import kz.hackalem.wamigos.meeting.service.MeetingJobService;
import org.springframework.stereotype.Component;

@Component
public class MeetingExportProcessor {

    private final MeetingJobService meetingJobService;
    private final AccessTokenManager accessTokenManager;
    private final ExportModelMapper exportModelMapper;
    private final Map<ExportFormat, ExportRenderer> renderers;
    private final Clock clock;

    public MeetingExportProcessor(
            MeetingJobService meetingJobService,
            AccessTokenManager accessTokenManager,
            ExportModelMapper exportModelMapper,
            List<ExportRenderer> renderers,
            Clock clock
    ) {
        this.meetingJobService = meetingJobService;
        this.accessTokenManager = accessTokenManager;
        this.exportModelMapper = exportModelMapper;
        this.renderers = Map.copyOf(renderers.stream().collect(Collectors.toMap(
                ExportRenderer::format,
                Function.identity(),
                (first, second) -> {
                    throw new IllegalStateException("Duplicate export renderer for " + first.format());
                },
                () -> new EnumMap<>(ExportFormat.class)
        )));
        this.clock = clock;
    }

    public ExportedDocument export(UUID id, String authorization, String formatValue) {
        ExportFormat format = ExportFormat.fromQuery(formatValue);
        String tokenHash = accessTokenManager.extractAndHash(authorization);
        MeetingJobDto job = meetingJobService.getAccessible(id, tokenHash, clock.instant());
        if (job.status() != MeetingStatus.COMPLETED || job.result() == null) {
            throw new ConflictException();
        }
        ExportRenderer renderer = renderers.get(format);
        if (renderer == null) {
            throw new IllegalStateException("Missing export renderer for " + format);
        }
        ExportModel model = exportModelMapper.toModel(job);
        return ExportedDocument.builder()
                .content(renderer.render(model))
                .mediaType(renderer.mediaType())
                .fileName("meeting-protocol." + format.name().toLowerCase())
                .build();
    }
}
