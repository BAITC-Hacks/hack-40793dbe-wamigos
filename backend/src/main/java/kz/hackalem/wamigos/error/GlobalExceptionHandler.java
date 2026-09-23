package kz.hackalem.wamigos.error;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
        return response(
                exception.getStatus(),
                exception.getCode(),
                exception.getTitle(),
                exception.getMessage(),
                null
        );
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiErrorResponse> handleValidation(Exception exception) {
        org.springframework.validation.BindingResult bindingResult = exception instanceof MethodArgumentNotValidException method
                ? method.getBindingResult()
                : ((BindException) exception).getBindingResult();
        List<FieldErrorResponse> errors = bindingResult.getFieldErrors().stream()
                .map(error -> FieldErrorResponse.builder()
                        .field(error.getField())
                        .message(error.getDefaultMessage())
                        .build())
                .toList();
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_REQUEST,
                "Некорректный запрос",
                "Проверьте параметры запроса.",
                errors
        );
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception exception) {
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_REQUEST,
                "Некорректный запрос",
                "Проверьте формат и обязательные параметры запроса.",
                null
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        return response(
                HttpStatus.PAYLOAD_TOO_LARGE,
                ErrorCode.FILE_TOO_LARGE,
                "Файл слишком большой",
                "Размер файла превышает установленный лимит.",
                null
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unexpected request failure", exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                "Внутренняя ошибка",
                "Не удалось выполнить запрос.",
                null
        );
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            ErrorCode code,
            String title,
            String detail,
            List<FieldErrorResponse> errors
    ) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .type("about:blank")
                .title(title)
                .status(status.value())
                .detail(detail)
                .code(code)
                .errors(errors)
                .build();
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
