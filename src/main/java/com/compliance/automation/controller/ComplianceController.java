package com.compliance.automation.controller;

import com.compliance.automation.exception.FileProcessingException;
import com.compliance.automation.model.ExpectedResult;
import com.compliance.automation.model.Report;
import com.compliance.automation.loader.ExpectedResultLoader;
import com.compliance.automation.orchestrator.ComplianceOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
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
        log.info("API request received: endpoint=/api/run, type={}, configSize={}",
            request == null ? null : request.type(),
            request == null || request.config() == null ? 0 : request.config().length());

        Report report = complianceOrchestrator.runCompliance(request.config(), List.of(), null);
        log.info("API request completed: endpoint=/api/run, total={}, passed={}, failed={}",
            report.getTotal(), report.getPassed(), report.getFailed());
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
            throw new FileProcessingException("Unable to read config file content.", exception);
        }

        log.info("Processing run-compliance request with type={}, configFile={}, expectedFile={}, pdfFilePresent={}",
                type,
                configFile.getOriginalFilename(),
                expectedFile.getOriginalFilename(),
                pdfFile != null && !pdfFile.isEmpty());

        List<ExpectedResult> expectedResults = expectedResultLoader.load(expectedFile);
        Report report = complianceOrchestrator.runCompliance(config, expectedResults, pdfFile);
        log.info("API request completed: endpoint=/api/run-compliance, total={}, passed={}, failed={}",
                report.getTotal(), report.getPassed(), report.getFailed());
        return ResponseEntity.ok(report);
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
}