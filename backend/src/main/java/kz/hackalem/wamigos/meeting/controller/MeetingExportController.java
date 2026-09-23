package kz.hackalem.wamigos.meeting.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.UUID;
import kz.hackalem.wamigos.error.ApiErrorResponse;
import kz.hackalem.wamigos.export.MeetingExportProcessor;
import kz.hackalem.wamigos.export.model.ExportedDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings")
public class MeetingExportController {

    private final MeetingExportProcessor meetingExportProcessor;

    @Operation(
            summary = "Экспортировать протокол",
            description = "Формирует PDF или DOCX из сохранённого результата без повторного запуска анализа."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Документ", content = @Content(mediaType = "application/octet-stream")),
            @ApiResponse(responseCode = "400", description = "Некорректный формат", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Запись недоступна", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Результат ещё не готов", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Ошибка экспорта", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{id}/export")
    public ResponseEntity<ByteArrayResource> export(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(name = "format", required = false) String format
    ) {
        ExportedDocument document = meetingExportProcessor.export(id, authorization, format);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(document.mediaType()))
                .contentLength(document.content().length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.fileName()).build().toString()
                )
                .body(new ByteArrayResource(document.content()));
    }
}
