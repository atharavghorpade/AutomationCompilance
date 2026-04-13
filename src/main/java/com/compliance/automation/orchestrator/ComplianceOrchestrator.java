package com.compliance.automation.orchestrator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.compliance.automation.exception.FileProcessingException;
import com.compliance.automation.exception.JsExecutionException;
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
		boolean pdfPresent = pdfFile != null && !pdfFile.isEmpty();
		log.info("Starting compliance pipeline (configSize={}, pdfFilePresent={})",
				safeConfig.length(),
				pdfPresent);

		List<Rule> rules = resolveRulesForExecution(pdfFile, pdfPresent); // Step 1
		Map<String, String> jsByRuleId = generateJavaScript(rules); // Step 2
		List<Result> results = executeRules(jsByRuleId, safeConfig); // Step 3
		List<ExpectedResult> safeExpectedResults = loadExpectedResults(expectedResults); // Step 4

		ValidationResult validationResult = validateResults(results, safeExpectedResults); // Step 5
		if (!validationResult.isMatched()) {
			results = retryFailedRules(results, validationResult, safeConfig, safeExpectedResults); // Step 6
		}

		Report report = generateReport(results, safeExpectedResults); // Step 7
		saveOutputs(jsByRuleId, report); // Step 8

		log.info("Compliance pipeline completed (total={}, passed={}, failed={})",
				report.getTotal(), report.getPassed(), report.getFailed());
		return report;
	}

	private List<Rule> resolveRulesForExecution(MultipartFile pdfFile, boolean pdfPresent) {
		if (pdfPresent) {
			return parsePdfExtractMetadataAndSave(pdfFile);
		}

		log.info("Step 1 skipped: no pdfFile provided. Loading default rules");
		return loadDefaultRules();
	}

	private List<Rule> parsePdfExtractMetadataAndSave(MultipartFile pdfFile) {
		log.info("Step 1: parsing PDF, extracting metadata, and saving metadata.json");

		Path tempPdf = null;
		try {
			tempPdf = Files.createTempFile("compliance-rules-", ".pdf");
			pdfFile.transferTo(tempPdf.toFile());
			log.info("PDF file received: {}", pdfFile.getOriginalFilename());

			String pdfText = pdfParser.extractText(tempPdf.toFile());
			List<Rule> extractedRules = metadataExtractor.extractRules(pdfText);
			fileStorageService.saveMetadata(extractedRules);
			log.info("Step 1 completed: extracted {} rules from PDF", extractedRules.size());

			if (extractedRules.isEmpty()) {
				log.warn("No rules extracted from PDF. Falling back to default rules for downstream execution");
				return loadDefaultRules();
			}
			return extractedRules;
		} catch (IOException exception) {
			log.error("Step 1 failed: unable to process uploaded PDF file", exception);
			throw new FileProcessingException("Failed to process uploaded PDF file.", exception);
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

	private List<Rule> loadDefaultRules() {
		List<Rule> defaultRules = metadataExtractor.extractRulesFromFile(null);
		log.info("Loaded {} default rules", defaultRules.size());
		return defaultRules;
	}

	private Map<String, String> generateJavaScript(List<Rule> rules) {
		log.info("Step 2: generating JavaScript checks for {} rules", rules.size());
		Map<String, String> jsByRuleId = jsGeneratorService.generateCheckFunctions(rules);
		log.info("Step 2 completed: generated JavaScript functions for {} rules", jsByRuleId.size());
		return jsByRuleId;
	}

	private List<ExpectedResult> loadExpectedResults(List<ExpectedResult> expectedResults) {
		log.info("Step 4: loading expected results");
		List<ExpectedResult> safeExpectedResults = Objects.requireNonNull(expectedResults,
				"expectedResults must not be null");
		log.info("Step 4 completed: loaded {} expected results", safeExpectedResults.size());
		return safeExpectedResults;
	}

	private ValidationResult validateResults(List<Result> results, List<ExpectedResult> expectedResults) {
		log.info("Step 5: validating execution results");
		ValidationResult validationResult = validationService.validate(results, expectedResults);
		log.info("Step 5 completed: validationMatched={}, failedRuleCount={}",
				validationResult.isMatched(),
				validationResult.getFailedRuleIds().size());
		return validationResult;
	}

	private List<Result> retryFailedRules(List<Result> currentResults,
			ValidationResult validationResult,
			String config,
			List<ExpectedResult> expectedResults) {
		log.info("Step 6: retrying {} failed rules", validationResult.getFailedRuleIds().size());
		Map<String, ExpectedResult> expectedByRuleId = mapExpectedByRuleId(expectedResults);
		List<Result> retriedResults = retryEngine.retryFailedRules(
				validationResult.getFailedRuleIds(),
				config,
				expectedByRuleId);

		mergeRetryResults(currentResults, retriedResults);
		log.info("Step 6 completed: merged {} retried results", retriedResults.size());
		return currentResults;
	}

	private Report generateReport(List<Result> results, List<ExpectedResult> expectedResults) {
		log.info("Step 7: generating report");
		Report report = reportGenerator.generateReport(results, expectedResults);
		log.info("Step 7 completed: total={}, passed={}, failed={}",
				report.getTotal(), report.getPassed(), report.getFailed());
		return report;
	}

	private void saveOutputs(Map<String, String> jsByRuleId, Report report) {
		log.info("Step 8: saving generated outputs");
		saveGeneratedJavaScript(jsByRuleId);
		String reportJson = reportGenerator.exportToJson(report);
		fileStorageService.saveReport("report.json", reportJson);
		log.info("Step 8 completed: outputs saved");
	}

	private List<Result> executeRules(Map<String, String> jsByRuleId, String config) {
		log.info("Step 3: executing JavaScript checks");
		List<Result> results = new ArrayList<>();
		for (Map.Entry<String, String> entry : jsByRuleId.entrySet()) {
			Result result = jsExecutor.execute(entry.getValue(), config, entry.getKey());
			results.add(result);
		}

		long errorCount = results.stream()
				.filter(result -> result != null && "ERROR".equalsIgnoreCase(result.getStatus()))
				.count();
		if (!results.isEmpty() && errorCount == results.size()) {
			throw new JsExecutionException("JavaScript execution failed for all generated rules.");
		}

		log.info("Step 3 completed: executed {} rules", results.size());
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
