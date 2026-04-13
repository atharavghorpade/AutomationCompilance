package com.compliance.automation.orchestrator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.compliance.automation.executor.JSExecutor;
import com.compliance.automation.generator.JSGeneratorService;
import com.compliance.automation.model.ExpectedResult;
import com.compliance.automation.model.Report;
import com.compliance.automation.model.Result;
import com.compliance.automation.model.Rule;
import com.compliance.automation.model.ValidationResult;
import com.compliance.automation.parser.MetadataExtractor;
import com.compliance.automation.parser.PDFParser;
import com.compliance.automation.report.ReportGenerator;
import com.compliance.automation.retry.RetryEngine;
import com.compliance.automation.storage.FileStorageService;
import com.compliance.automation.validator.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ComplianceOrchestrator {

	private static final Logger log = LoggerFactory.getLogger(ComplianceOrchestrator.class);

	private final MetadataExtractor metadataExtractor;
	private final PDFParser pdfParser;
	private final JSGeneratorService jsGeneratorService;
	private final JSExecutor jsExecutor;
	private final ValidationService validationService;
	private final RetryEngine retryEngine;
	private final ReportGenerator reportGenerator;
	private final FileStorageService fileStorageService;

	public ComplianceOrchestrator(
			MetadataExtractor metadataExtractor,
			PDFParser pdfParser,
			JSGeneratorService jsGeneratorService,
			JSExecutor jsExecutor,
			ValidationService validationService,
			RetryEngine retryEngine,
			ReportGenerator reportGenerator,
			FileStorageService fileStorageService) {
		this.metadataExtractor = metadataExtractor;
		this.pdfParser = pdfParser;
		this.jsGeneratorService = jsGeneratorService;
		this.jsExecutor = jsExecutor;
		this.validationService = validationService;
		this.retryEngine = retryEngine;
		this.reportGenerator = reportGenerator;
		this.fileStorageService = fileStorageService;
	}

	public Report runCompliance(String config, List<ExpectedResult> expectedResults, MultipartFile pdfFile) {
		String safeConfig = config == null ? "" : config;
		log.info("Starting compliance pipeline (configSize={}, pdfFilePresent={})",
				safeConfig.length(),
				pdfFile != null && !pdfFile.isEmpty());

		List<Rule> rules = loadRules(pdfFile); // Step 1
		Map<String, String> jsByRuleId = generateJavaScript(rules); // Step 2
		List<Result> results = executeRules(jsByRuleId, safeConfig); // Step 3
		List<ExpectedResult> safeExpectedResults = loadExpectedResults(expectedResults); // Step 4

		ValidationResult validationResult = validationService.validate(results, safeExpectedResults); // Step 5
		if (!validationResult.isMatched()) {
			results = retryFailedRules(results, validationResult, safeConfig, safeExpectedResults); // Step 6
		}

		Report report = reportGenerator.generateReport(results, safeExpectedResults); // Step 7
		saveOutputs(jsByRuleId, report); // Step 8

		log.info("Compliance pipeline completed (total={}, passed={}, failed={})",
				report.getTotal(), report.getPassed(), report.getFailed());
		return report;
	}

	private List<Rule> loadRules(MultipartFile pdfFile) {
		if (pdfFile == null || pdfFile.isEmpty()) {
			log.info("No pdfFile provided. Using fallback metadata source");
			return metadataExtractor.extractRulesFromFile(null);
		}

		Path tempPdf = null;
		try {
			tempPdf = Files.createTempFile("compliance-rules-", ".pdf");
			pdfFile.transferTo(tempPdf.toFile());
			log.info("PDF file received: {}", pdfFile.getOriginalFilename());

			String pdfText = pdfParser.extractText(tempPdf.toFile());
			List<Rule> extractedRules = metadataExtractor.extractRules(pdfText);

			if (extractedRules.isEmpty()) {
				log.warn("No rules extracted from PDF. Falling back to default rules");
				extractedRules = metadataExtractor.extractRulesFromFile(null);
			}

			fileStorageService.saveMetadata(extractedRules);
			return extractedRules;
		} catch (IOException exception) {
			log.error("Failed to process uploaded PDF file", exception);
			throw new RuntimeException("Failed to process uploaded PDF file", exception);
		} finally {
			if (tempPdf != null) {
				try {
					Files.deleteIfExists(tempPdf);
				} catch (IOException ignored) {
					log.debug("Could not delete temp PDF file: {}", tempPdf);
				}
			}
		}
	}

	private Map<String, String> generateJavaScript(List<Rule> rules) {
		Map<String, String> jsByRuleId = jsGeneratorService.generateCheckFunctions(rules);
		log.info("Generated JavaScript functions for {} rules", jsByRuleId.size());
		return jsByRuleId;
	}

	private List<ExpectedResult> loadExpectedResults(List<ExpectedResult> expectedResults) {
		List<ExpectedResult> safeExpectedResults = Objects.requireNonNull(expectedResults,
				"expectedResults must not be null");
		log.info("Loaded {} expected results", safeExpectedResults.size());
		return safeExpectedResults;
	}

	private List<Result> retryFailedRules(List<Result> currentResults,
			ValidationResult validationResult,
			String config,
			List<ExpectedResult> expectedResults) {
		Map<String, ExpectedResult> expectedByRuleId = mapExpectedByRuleId(expectedResults);
		List<Result> retriedResults = retryEngine.retryFailedRules(
				validationResult.getFailedRuleIds(),
				config,
				expectedByRuleId);

		mergeRetryResults(currentResults, retriedResults);
		return currentResults;
	}

	private void saveOutputs(Map<String, String> jsByRuleId, Report report) {
		saveGeneratedJavaScript(jsByRuleId);
		String reportJson = reportGenerator.exportToJson(report);
		fileStorageService.saveReport("report.json", reportJson);
	}

	private List<Result> executeRules(Map<String, String> jsByRuleId, String config) {
		List<Result> results = new ArrayList<>();
		for (Map.Entry<String, String> entry : jsByRuleId.entrySet()) {
			Result result = jsExecutor.execute(entry.getValue(), config, entry.getKey());
			results.add(result);
		}
		return results;
	}

	private Map<String, ExpectedResult> mapExpectedByRuleId(List<ExpectedResult> expectedResults) {
		Map<String, ExpectedResult> expectedByRuleId = new HashMap<>();
		for (ExpectedResult expectedResult : expectedResults) {
			expectedByRuleId.put(expectedResult.getRuleId(), expectedResult);
		}
		return expectedByRuleId;
	}

	private void mergeRetryResults(List<Result> originalResults, List<Result> retryResults) {
		Map<String, Result> retryByRuleId = new HashMap<>();
		for (Result retryResult : retryResults) {
			retryByRuleId.put(retryResult.getRuleId(), retryResult);
		}

		for (int i = 0; i < originalResults.size(); i++) {
			Result current = originalResults.get(i);
			Result retried = retryByRuleId.get(current.getRuleId());
			if (retried != null) {
				originalResults.set(i, retried);
			}
		}
	}

	private void saveGeneratedJavaScript(Map<String, String> jsByRuleId) {
		for (Map.Entry<String, String> entry : jsByRuleId.entrySet()) {
			fileStorageService.saveJavaScript(entry.getKey(), entry.getValue());
		}
	}
}
