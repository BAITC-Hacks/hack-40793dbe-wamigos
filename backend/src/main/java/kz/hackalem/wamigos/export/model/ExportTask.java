package kz.hackalem.wamigos.export.model;

import lombok.Builder;

@Builder
public record ExportTask(String action, String assignee, String assigner, String deadline) {
}
