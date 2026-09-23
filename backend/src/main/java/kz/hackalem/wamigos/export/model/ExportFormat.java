package kz.hackalem.wamigos.export.model;

import kz.hackalem.wamigos.error.ValidationException;

public enum ExportFormat {
    PDF,
    DOCX;

    public static ExportFormat fromQuery(String value) {
        if (value == null) {
            throw new ValidationException("Параметр format обязателен.");
        }
        return switch (value) {
            case "pdf" -> PDF;
            case "docx" -> DOCX;
            default -> throw new ValidationException("Параметр format должен быть равен pdf или docx.");
        };
    }
}
