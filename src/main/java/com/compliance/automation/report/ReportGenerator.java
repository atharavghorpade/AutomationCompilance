package com.compliance.automation.report;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.compliance.automation.exception.FileProcessingException;
import com.compliance.automation.model.ExpectedResult;
import com.compliance.automation.model.Report;
import com.compliance.automation.model.Result;
import com.compliance.automation.model.RuleReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReportGenerator {

	private static final Logger log = LoggerFactory.getLogger(ReportGenerator.class);

	private final ObjectMapper objectMapper;

	public ReportGenerator(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public Report generateReport(List<Result> actualResults, List<ExpectedResult> expectedResults) {
		log.info("Generating structured report from {} actual results and {} expected results",
				actualResults == null ? 0 : actualResults.size(),
				expectedResults == null ? 0 : expectedResults.size());

		Map<String, Result> actualByRuleId = toActualMap(actualResults);
		Map<String, ExpectedResult> expectedByRuleId = toExpectedMap(expectedResults);

		List<RuleReport> reportRows = new ArrayList<>();
		int passed = 0;
		int failed = 0;

		for (Map.Entry<String, ExpectedResult> expectedEntry : expectedByRuleId.entrySet()) {
			String ruleId = expectedEntry.getKey();
			ExpectedResult expected = expectedEntry.getValue();
			Result actual = actualByRuleId.get(ruleId);

			String actualStatus = actual != null ? actual.getStatus() : null;
			String expectedStatus = expected != null ? expected.getStatus() : null;
			int actualLine = actual != null ? actual.getLineNumber() : -1;
			int expectedLine = expected != null ? expected.getLineNumber() : -1;

			boolean statusMatch = equalsIgnoreCase(actualStatus, expectedStatus);
			boolean lineMatch = actualLine == expectedLine;
			String validation = statusMatch && lineMatch ? "MATCH" : "MISMATCH";

			reportRows.add(new RuleReport(ruleId, actualStatus, expectedStatus, actualLine, validation));
			log.debug("Report row built for ruleId={}: actualStatus={}, expectedStatus={}, lineNumber={}, validation={}",
					ruleId, actualStatus, expectedStatus, actualLine, validation);

			if ("MATCH".equals(validation)) {
				passed++;
			} else {
				failed++;
			}
		}

		for (Map.Entry<String, Result> actualEntry : actualByRuleId.entrySet()) {
			String ruleId = actualEntry.getKey();
			if (!expectedByRuleId.containsKey(ruleId)) {
				Result actual = actualEntry.getValue();
				reportRows.add(new RuleReport(
						ruleId,
						actual.getStatus(),
						null,
						actual.getLineNumber(),
						"MISMATCH"));
				failed++;
			}
		}

		int total = reportRows.size();
		Report report = new Report(reportRows, total, passed, failed);
		log.info("Structured report generated: total={}, passed={}, failed={}", total, passed, failed);
		return report;
	}

	public String exportToJson(Report report) {
		if (report == null) {
			throw new IllegalArgumentException("Report must not be null");
		}

		try {
			String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
			log.info("Exported report to JSON (chars={})", json.length());
			return json;
		} catch (IOException exception) {
			log.error("Failed to export report to JSON", exception);
			throw new FileProcessingException("Failed to export report to JSON.", exception);
		}
	}

	private boolean equalsIgnoreCase(String left, String right) {
		if (left == null || right == null) {
			return left == right;
		}
		return left.equalsIgnoreCase(right);
	}

	private Map<String, Result> toActualMap(List<Result> actualResults) {
		Map<String, Result> actualByRuleId = new LinkedHashMap<>();
		if (actualResults == null) {
			return actualByRuleId;
		}

		for (Result result : actualResults) {
			if (result == null || result.getRuleId() == null) {
				continue;
			}
			actualByRuleId.put(result.getRuleId(), result);
		}
		return actualByRuleId;
	}

	private Map<String, ExpectedResult> toExpectedMap(List<ExpectedResult> expectedResults) {
		Map<String, ExpectedResult> expectedByRuleId = new LinkedHashMap<>();
		if (expectedResults == null) {
			return expectedByRuleId;
		}

		for (ExpectedResult expected : expectedResults) {
			if (expected == null || expected.getRuleId() == null) {
				continue;
			}
			expectedByRuleId.put(expected.getRuleId(), expected);
		}
		return expectedByRuleId;
	}
}
