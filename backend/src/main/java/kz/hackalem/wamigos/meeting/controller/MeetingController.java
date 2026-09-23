package kz.hackalem.wamigos.meeting.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.UUID;
import kz.hackalem.wamigos.error.ApiErrorResponse;
import kz.hackalem.wamigos.meeting.dto.CreateMeetingRequest;
import kz.hackalem.wamigos.meeting.dto.MeetingCreatedResponse;
import kz.hackalem.wamigos.meeting.dto.MeetingResponse;
import kz.hackalem.wamigos.meeting.processor.MeetingProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private final MeetingProcessor meetingProcessor;

    @Operation(
            summary = "Создать обработку записи",
            description = "Сохраняет запись и ставит её в постоянную очередь. Возвращает одноразовый токен доступа."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Запись принята"),
            @ApiResponse(responseCode = "400", description = "Некорректные параметры", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "Файл слишком большой", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Формат не поддерживается", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Очередь заполнена", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<MeetingCreatedResponse> create(@Valid @ModelAttribute CreateMeetingRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(meetingProcessor.create(request));
    }

    @Operation(
            summary = "Получить статус и результат",
            description = "Возвращает текущее состояние и единый результат обработки по Bearer-токену записи."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Состояние обработки"),
            @ApiResponse(responseCode = "400", description = "Некорректный UUID", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Запись недоступна", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MeetingResponse> get(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(meetingProcessor.get(id, authorization));
    }
}
