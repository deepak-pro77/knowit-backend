package com.knowitai.exception;

import com.knowitai.ai.AiProviderException;
import com.knowitai.dto.ApiError;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Turns every failure into a safe, consistent JSON error. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidImageException.class)
    public ResponseEntity<ApiError> handleInvalidImage(InvalidImageException ex) {
        return ResponseEntity.status(ex.getReason().status())
                .body(new ApiError(ex.getReason().name(), ex.getMessage()));
    }

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ApiError> handleAiFailure(AiProviderException ex) {
        // The detail goes to our log; the client only sees the safe message for this kind of failure.
        log.warn("AI call failed: kind={} detail={}", ex.getKind(), ex.getMessage(), ex.getCause());
        return ResponseEntity.status(ex.getKind().status())
                .body(new ApiError(ex.getKind().name(), ex.getKind().clientMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("VALIDATION_ERROR", message.isEmpty() ? "Invalid request." : message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("INVALID_REQUEST", "The request body is missing or malformed."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooBig(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiError("FILE_TOO_LARGE", "The image is larger than the maximum allowed size."));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("MISSING_IMAGE", "Please attach an image in the 'image' field."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAnythingElse(Exception ex) {
        // Spring's own web exceptions (wrong HTTP method, bad media type...) already carry the right status.
        if (ex instanceof ErrorResponse springError) {
            return ResponseEntity.status(springError.getStatusCode())
                    .body(new ApiError("REQUEST_ERROR", "The request could not be processed."));
        }
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "Something went wrong. Please try again."));
    }
}
