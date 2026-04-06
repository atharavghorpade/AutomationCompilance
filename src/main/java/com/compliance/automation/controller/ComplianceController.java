package com.compliance.automation.controller;

import com.compliance.automation.model.ExpectedResult;
import com.compliance.automation.model.Report;
import com.compliance.automation.loader.ExpectedResultLoader;
import com.compliance.automation.loader.ExpectedResultLoaderException;
import com.compliance.automation.orchestrator.ComplianceOrchestrator;
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

    private final ComplianceOrchestrator complianceOrchestrator;
    private final ExpectedResultLoader expectedResultLoader;

    public ComplianceController(ComplianceOrchestrator complianceOrchestrator, ExpectedResultLoader expectedResultLoader) {
        this.complianceOrchestrator = complianceOrchestrator;
        this.expectedResultLoader = expectedResultLoader;
    }

    @PostMapping("/run")
    public ResponseEntity<Report> run(@RequestBody RunRequest request) {
        Report report = complianceOrchestrator.runCompliance(request.config(), null);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/run-compliance")
    public ResponseEntity<Report> runCompliance(
            @RequestParam("configFile") MultipartFile configFile,
            @RequestParam("expectedFile") MultipartFile expectedFile,
            @RequestParam("type") String type) throws IOException {
        String config = configFile == null ? "" : new String(configFile.getBytes(), StandardCharsets.UTF_8);
        List<ExpectedResult> expectedResults = expectedResultLoader.load(expectedFile);
        Report report = complianceOrchestrator.runCompliance(config, expectedResults);
        return ResponseEntity.ok(report);
    }

    @ExceptionHandler(ExpectedResultLoaderException.class)
    public ResponseEntity<String> handleExpectedResultLoaderException(ExpectedResultLoaderException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    public record RunRequest(String config, String type) {
    }
}