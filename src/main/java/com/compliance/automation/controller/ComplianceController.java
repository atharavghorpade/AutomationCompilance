package com.compliance.automation.controller;

import com.compliance.automation.model.Report;
import com.compliance.automation.orchestrator.ComplianceOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ComplianceController {

    private final ComplianceOrchestrator complianceOrchestrator;

    public ComplianceController(ComplianceOrchestrator complianceOrchestrator) {
        this.complianceOrchestrator = complianceOrchestrator;
    }

    @PostMapping("/run")
    public ResponseEntity<Report> run(@RequestBody RunRequest request) {
        Report report = complianceOrchestrator.runCompliance(request.config());
        return ResponseEntity.ok(report);
    }

    public record RunRequest(String config, String type) {
    }
}