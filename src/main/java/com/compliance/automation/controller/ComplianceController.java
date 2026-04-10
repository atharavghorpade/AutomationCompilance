package com.compliance.automation.controller;

import com.compliance.automation.model.ExpectedResult;
import com.compliance.automation.model.Report;
import com.compliance.automation.loader.ExpectedResultLoader;
import com.compliance.automation.loader.ExpectedResultLoaderException;
import com.compliance.automation.orchestrator.ComplianceOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ComplianceController {

    private static final Logger log = LoggerFactory.getLogger(ComplianceController.class);

    private final ComplianceOrchestrator complianceOrchestrator;
    private final ExpectedResultLoader expectedResultLoader;

    public ComplianceController(ComplianceOrchestrator complianceOrchestrator, ExpectedResultLoader expectedResultLoader) {
        this.complianceOrchestrator = complianceOrchestrator;
        this.expectedResultLoader = expectedResultLoader;
    }

    @PostMapping("/run")
    public ResponseEntity<Report> run(@RequestBody RunRequest request) {
        Report report = complianceOrchestrator.runCompliance(request.config(), List.of(), null);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/run-compliance")
    public ResponseEntity<Report> runCompliance(
            @RequestParam("configFile") MultipartFile configFile,
            @RequestParam("expectedFile") MultipartFile expectedFile,
            @RequestParam(value = "pdfFile", required = false) MultipartFile pdfFile,
            @RequestParam("type") String type) {
        validateRequest(configFile, expectedFile, type);

        String config;
        try {
            config = new String(configFile.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            log.warn("Unable to read config file content", exception);
            throw new IllegalArgumentException("Unable to read configFile content.", exception);
        }

        log.info("Processing run-compliance request with type={}, configFile={}, expectedFile={}, pdfFilePresent={}",
                type,
                configFile.getOriginalFilename(),
                expectedFile.getOriginalFilename(),
                pdfFile != null && !pdfFile.isEmpty());

        List<ExpectedResult> expectedResults = expectedResultLoader.load(expectedFile);

        try {
            Report report = complianceOrchestrator.runCompliance(config, expectedResults, pdfFile);
            return ResponseEntity.ok(report);
        } catch (RuntimeException exception) {
            log.error("Failed to process run-compliance request", exception);
            throw exception;
        }
    }

    @ExceptionHandler(ExpectedResultLoaderException.class)
    public ResponseEntity<ApiErrorResponse> handleExpectedResultLoaderException(ExpectedResultLoaderException ex) {
        log.warn("Expected result parsing failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("INVALID_EXPECTED_FILE", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Request validation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("INVALID_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("PROCESSING_FAILED", "Failed to process compliance run."));
    }

    private void validateRequest(MultipartFile configFile, MultipartFile expectedFile, String type) {
        if (configFile == null || configFile.isEmpty()) {
            throw new IllegalArgumentException("configFile is required and must not be empty.");
        }

        if (expectedFile == null || expectedFile.isEmpty()) {
            throw new IllegalArgumentException("expectedFile is required and must not be empty.");
        }

        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type is required and must be either CIS or STIG.");
        }

        String normalizedType = type.trim().toUpperCase();
        if (!"CIS".equals(normalizedType) && !"STIG".equals(normalizedType)) {
            throw new IllegalArgumentException("type must be either CIS or STIG.");
        }
    }

    public record RunRequest(String config, String type) {
    }

    public record ApiErrorResponse(String code, String message) {
    }
}