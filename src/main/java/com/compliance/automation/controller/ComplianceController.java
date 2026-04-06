package com.compliance.automation.controller;

import com.compliance.automation.model.ExpectedResult;
import com.compliance.automation.model.Report;
import com.compliance.automation.orchestrator.ComplianceOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ComplianceOrchestrator complianceOrchestrator;
    private final ObjectMapper objectMapper;

    public ComplianceController(ComplianceOrchestrator complianceOrchestrator, ObjectMapper objectMapper) {
        this.complianceOrchestrator = complianceOrchestrator;
        this.objectMapper = objectMapper;
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
        
        // Convert configFile to String
        String config = new String(configFile.getBytes(), StandardCharsets.UTF_8);
        
        // Parse expectedFile as JSON to List<ExpectedResult>
        List<ExpectedResult> expectedResults = objectMapper.readValue(
                expectedFile.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ExpectedResult.class)
        );
        
        // Pass both to ComplianceOrchestrator
        Report report = complianceOrchestrator.runCompliance(config, expectedResults);
        return ResponseEntity.ok(report);
    }

    public record RunRequest(String config, String type) {
    }
}