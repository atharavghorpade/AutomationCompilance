package com.compliance.automation.orchestrator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.compliance.automation.executor.JSExecutor;
import com.compliance.automation.generator.JSGeneratorService;
import com.compliance.automation.model.ExpectedResult;
import com.compliance.automation.model.Report;
import com.compliance.automation.model.Result;
import com.compliance.automation.model.Rule;
import com.compliance.automation.model.ValidationResult;
import com.compliance.automation.parser.MetadataExtractor;
import com.compliance.automation.report.ReportGenerator;
import com.compliance.automation.retry.RetryEngine;
import com.compliance.automation.storage.FileStorageService;
import com.compliance.automation.validator.ValidationService;
import org.springframework.stereotype.Service;

@Service
public class ComplianceOrchestrator {

	private final MetadataExtractor metadataExtractor;
	private final JSGeneratorService jsGeneratorService;
	private final JSExecutor jsExecutor;
	private final ValidationService validationService;
	private final RetryEngine retryEngine;
	private final ReportGenerator reportGenerator;
	private final FileStorageService fileStorageService;

	public ComplianceOrchestrator(
			MetadataExtractor metadataExtractor,
			JSGeneratorService jsGeneratorService,
			JSExecutor jsExecutor,
			ValidationService validationService,
			RetryEngine retryEngine,
			ReportGenerator reportGenerator,
			FileStorageService fileStorageService) {
		this.metadataExtractor = metadataExtractor;
		this.jsGeneratorService = jsGeneratorService;
		this.jsExecutor = jsExecutor;
		this.validationService = validationService;
		this.retryEngine = retryEngine;
		this.reportGenerator = reportGenerator;
		this.fileStorageService = fileStorageService;
	}

	public Report runCompliance(String config) {
		List<Rule> rules = metadataExtractor.extractRulesFromFile(null);

		Map<String, String> jsByRuleId = jsGeneratorService.generateCheckFunctions(rules);
		saveGeneratedJavaScript(jsByRuleId);

		List<Result> results = executeRules(jsByRuleId, config);
		List<ExpectedResult> expectedResults = buildExpectedResults(rules, config);

		ValidationResult validationResult = validationService.validate(results, expectedResults);
		if (!validationResult.isMatched()) {
			Map<String, String> expectedCommandByRuleId = mapExpectedCommandsByRuleId(rules);
			List<Result> retriedResults = retryEngine.retryFailedRules(
					validationResult.getFailedRuleIds(),
					config,
					expectedCommandByRuleId);
			mergeRetryResults(results, retriedResults);
		}

		return reportGenerator.generateReport(results);
	}

	private List<Result> executeRules(Map<String, String> jsByRuleId, String config) {
		List<Result> results = new ArrayList<>();
		for (Map.Entry<String, String> entry : jsByRuleId.entrySet()) {
			Result result = jsExecutor.execute(entry.getValue(), config, entry.getKey());
			results.add(result);
		}
		return results;
	}

	private List<ExpectedResult> buildExpectedResults(List<Rule> rules, String config) {
		List<ExpectedResult> expectedResults = new ArrayList<>();
		String safeConfig = config == null ? "" : config;

		for (Rule rule : rules) {
			boolean commandPresent = safeConfig.contains(rule.getExpectedCommand());
			expectedResults.add(new ExpectedResult(
					rule.getRuleId(),
					commandPresent ? "PASS" : "FAIL",
					commandPresent ? 1 : -1));
		}

		return expectedResults;
	}

	private Map<String, String> mapExpectedCommandsByRuleId(List<Rule> rules) {
		Map<String, String> expectedCommandByRuleId = new HashMap<>();
		for (Rule rule : rules) {
			expectedCommandByRuleId.put(rule.getRuleId(), rule.getExpectedCommand());
		}
		return expectedCommandByRuleId;
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
			String fileName = entry.getKey().replace('.', '_') + ".js";
			fileStorageService.saveJavaScript(fileName, entry.getValue());
		}
	}
}
