package com.compliance.automation.service;

import com.compliance.automation.exception.FileProcessingException;
import com.compliance.automation.loader.ExpectedResultLoader;
import com.compliance.automation.model.ComplianceType;
import com.compliance.automation.model.ExpectedResult;
import com.compliance.automation.model.Report;
import com.compliance.automation.orchestrator.ComplianceOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class ComplianceRequestService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceRequestService.class);

    private final ComplianceOrchestrator complianceOrchestrator;
    private final ExpectedResultLoader expectedResultLoader;

    public ComplianceRequestService(ComplianceOrchestrator complianceOrchestrator,
            ExpectedResultLoader expectedResultLoader) {
        this.complianceOrchestrator = complianceOrchestrator;
        this.expectedResultLoader = expectedResultLoader;
    }

    public Report runFromBody(String config, String type) {
        validateType(type);
        String safeConfig = config == null ? "" : config;
        return complianceOrchestrator.runCompliance(safeConfig, List.of(), null);
    }

    public Report runWithFiles(MultipartFile configFile, MultipartFile expectedFile, MultipartFile pdfFile, String type) {
        validateRunComplianceRequest(configFile, expectedFile, type);

        String config = readConfig(configFile);
        List<ExpectedResult> expectedResults = expectedResultLoader.load(expectedFile);

        log.info("Delegating compliance execution for type={}, configFile={}, expectedFile={}, pdfFilePresent={}",
                ComplianceType.from(type),
                configFile.getOriginalFilename(),
                expectedFile.getOriginalFilename(),
                pdfFile != null && !pdfFile.isEmpty());

        return complianceOrchestrator.runCompliance(config, expectedResults, pdfFile);
    }

    private String readConfig(MultipartFile configFile) {
        try {
            return new String(configFile.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new FileProcessingException("Unable to read config file content.", exception);
        }
    }

    private void validateRunComplianceRequest(MultipartFile configFile, MultipartFile expectedFile, String type) {
        if (configFile == null || configFile.isEmpty()) {
            throw new IllegalArgumentException("configFile is required and must not be empty.");
        }

        if (expectedFile == null || expectedFile.isEmpty()) {
            throw new IllegalArgumentException("expectedFile is required and must not be empty.");
        }

        validateType(type);
    }

    private ComplianceType validateType(String type) {
        return ComplianceType.from(type);
    }
}
