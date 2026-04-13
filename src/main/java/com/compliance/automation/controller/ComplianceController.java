package com.compliance.automation.controller;

import com.compliance.automation.model.Report;
import com.compliance.automation.service.ComplianceRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ComplianceController {

    private static final Logger log = LoggerFactory.getLogger(ComplianceController.class);

    private final ComplianceRequestService complianceRequestService;

    public ComplianceController(ComplianceRequestService complianceRequestService) {
        this.complianceRequestService = complianceRequestService;
    }

    @PostMapping("/run")
    public ResponseEntity<Report> run(@RequestBody RunRequest request) {
        log.info("API request received: endpoint=/api/run, type={}, configSize={}",
            request == null ? null : request.type(),
            request == null || request.config() == null ? 0 : request.config().length());

        Report report = complianceRequestService.runFromBody(
                request == null ? null : request.config(),
                request == null ? null : request.type());
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
        log.info("Processing run-compliance request with type={}, configFile={}, expectedFile={}, pdfFilePresent={}",
                type,
                configFile.getOriginalFilename(),
                expectedFile.getOriginalFilename(),
                pdfFile != null && !pdfFile.isEmpty());

        Report report = complianceRequestService.runWithFiles(configFile, expectedFile, pdfFile, type);
        log.info("API request completed: endpoint=/api/run-compliance, total={}, passed={}, failed={}",
                report.getTotal(), report.getPassed(), report.getFailed());
        return ResponseEntity.ok(report);
    }

    public record RunRequest(String config, String type) {
    }
}