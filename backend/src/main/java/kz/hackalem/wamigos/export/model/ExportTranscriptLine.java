package kz.hackalem.wamigos.export.model;

import lombok.Builder;

@Builder
public record ExportTranscriptLine(String timeCode, String speaker, String text) {
}
