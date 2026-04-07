package com.compliance.automation.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.compliance.automation.model.ExpectedResult;
import com.compliance.automation.model.Report;
import com.compliance.automation.model.Result;
import com.compliance.automation.model.RuleReport;
import org.springframework.stereotype.Service;

@Service
public class ReportGenerator {

	public Report generateReport(List<Result> actualResults, List<ExpectedResult> expectedResults) {
		Map<String, Result> actualByRuleId = new LinkedHashMap<>();
		if (actualResults != null) {
			for (Result result : actualResults) {
				if (result != null && result.getRuleId() != null) {
					actualByRuleId.put(result.getRuleId(), result);
				}
			}
		}

		Map<String, ExpectedResult> expectedByRuleId = new LinkedHashMap<>();
		if (expectedResults != null) {
			for (ExpectedResult expected : expectedResults) {
				if (expected != null && expected.getRuleId() != null) {
					expectedByRuleId.put(expected.getRuleId(), expected);
				}
			}
		}

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
		return new Report(reportRows, total, passed, failed);
	}

	private boolean equalsIgnoreCase(String left, String right) {
		if (left == null || right == null) {
			return left == right;
		}
		return left.equalsIgnoreCase(right);
	}
}
