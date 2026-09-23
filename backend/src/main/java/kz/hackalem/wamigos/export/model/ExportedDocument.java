package kz.hackalem.wamigos.export.model;

import lombok.Builder;

@Builder
public record ExportedDocument(byte[] content, String mediaType, String fileName) {
}
