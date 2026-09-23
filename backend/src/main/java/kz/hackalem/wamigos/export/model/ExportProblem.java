package kz.hackalem.wamigos.export.model;

import lombok.Builder;

@Builder
public record ExportProblem(String text, String reportedBy) {
}
