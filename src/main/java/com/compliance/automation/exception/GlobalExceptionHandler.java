package com.compliance.automation.exception;

import com.compliance.automation.loader.ExpectedResultLoaderException;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.Instant;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ExpectedResultLoaderException.class)
    public ResponseEntity<ApiErrorResponse> handleExpectedResultLoaderException(
            ExpectedResultLoaderException exception,
            HttpServletRequest request) {
        Throwable cause = exception.getCause();
        if (cause instanceof JsonProcessingException) {
            log.warn("JSON parsing error while reading expected results: {}", exception.getMessage());
            return build(HttpStatus.BAD_REQUEST,
                    "JSON_PARSE_ERROR",
                    exception.getMessage(),
                    request.getRequestURI());
        }

        log.warn("File processing error while reading expected results: {}", exception.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                "FILE_ERROR",
                exception.getMessage(),
                request.getRequestURI());
    }

    @ExceptionHandler({JsonProcessingException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorResponse> handleJsonParsingException(Exception exception, HttpServletRequest request) {
        log.warn("JSON parsing error for request {}: {}", request.getRequestURI(), exception.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                "JSON_PARSE_ERROR",
                "Invalid JSON payload. Please verify request format.",
                request.getRequestURI());
    }

    @ExceptionHandler(FileProcessingException.class)
    public ResponseEntity<ApiErrorResponse> handleFileProcessingException(
            FileProcessingException exception,
            HttpServletRequest request) {
        log.error("File processing failed for request {}: {}", request.getRequestURI(), exception.getMessage(), exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "FILE_PROCESSING_ERROR",
                exception.getMessage(),
                request.getRequestURI());
    }

    @ExceptionHandler(LlmProcessingException.class)
    public ResponseEntity<ApiErrorResponse> handleLlmProcessingException(
            LlmProcessingException exception,
            HttpServletRequest request) {
        log.error("LLM processing failed for request {}: {}", request.getRequestURI(), exception.getMessage(), exception);
        return build(HttpStatus.BAD_GATEWAY,
                "LLM_ERROR",
                exception.getMessage(),
                request.getRequestURI());
    }

    @ExceptionHandler(JsExecutionException.class)
    public ResponseEntity<ApiErrorResponse> handleJsExecutionException(
            JsExecutionException exception,
            HttpServletRequest request) {
        log.error("JavaScript execution failed for request {}: {}", request.getRequestURI(), exception.getMessage(), exception);
        return build(HttpStatus.UNPROCESSABLE_ENTITY,
                "JS_EXECUTION_ERROR",
                exception.getMessage(),
                request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        log.warn("Invalid request {}: {}", request.getRequestURI(), exception.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                exception.getMessage(),
                request.getRequestURI());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntimeException(
            RuntimeException exception,
            HttpServletRequest request) {
        log.error("Unhandled processing error for request {}", request.getRequestURI(), exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "PROCESSING_FAILED",
                "Failed to process compliance run.",
                request.getRequestURI());
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message, String path) {
        ApiErrorResponse response = new ApiErrorResponse(code, message, path, Instant.now());
        return ResponseEntity.status(status).body(response);
    }
}
