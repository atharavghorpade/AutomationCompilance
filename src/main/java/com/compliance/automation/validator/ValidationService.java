package com.compliance.automation.validator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.compliance.automation.model.ExpectedResult;
import com.compliance.automation.model.Result;
import com.compliance.automation.model.ValidationResult;
import org.springframework.stereotype.Service;

@Service
public class ValidationService {

    public ValidationResult validate(List<Result> actualResults, List<ExpectedResult> expectedResults) {
        List<String> failedRuleIds = new ArrayList<>();
        Map<String, String> mismatchReasons = new LinkedHashMap<>();

        Map<String, ExpectedResult> expectedMap = expectedResults.stream()
                .collect(Collectors.toMap(ExpectedResult::getRuleId, e -> e));

        Map<String, Result> actualMap = actualResults.stream()
                .collect(Collectors.toMap(Result::getRuleId, r -> r, (first, second) -> first));

        for (Result actual : actualResults) {
            ExpectedResult expected = expectedMap.get(actual.getRuleId());

            if (expected == null) {
                addFailure(failedRuleIds, mismatchReasons, actual.getRuleId(),
                        "Missing expected result for ruleId " + actual.getRuleId());
                continue;
            }

            List<String> mismatches = new ArrayList<>();

            if (!statusMatches(actual.getStatus(), expected.getStatus())) {
                mismatches.add("status mismatch: expected=" + expected.getStatus() + ", actual=" + actual.getStatus());
            }

            if (!lineNumberMatches(actual.getLineNumber(), expected.getLineNumber())) {
                mismatches.add("lineNumber mismatch: expected=" + expected.getLineNumber() + ", actual=" + actual.getLineNumber());
            }

            if (!mismatches.isEmpty()) {
                addFailure(failedRuleIds, mismatchReasons, actual.getRuleId(), String.join("; ", mismatches));
            }
        }

        for (ExpectedResult expected : expectedResults) {
            if (!actualMap.containsKey(expected.getRuleId())) {
                addFailure(failedRuleIds, mismatchReasons, expected.getRuleId(),
                        "Missing actual result for ruleId " + expected.getRuleId());
            }
        }

        boolean matched = failedRuleIds.isEmpty();
        return new ValidationResult(matched, failedRuleIds, mismatchReasons);
    }

    private void addFailure(List<String> failedRuleIds, Map<String, String> mismatchReasons,
                            String ruleId, String reason) {
        if (!failedRuleIds.contains(ruleId)) {
            failedRuleIds.add(ruleId);
        }
        mismatchReasons.put(ruleId, reason);
    }

    private boolean statusMatches(String actualStatus, String expectedStatus) {
        if (actualStatus == null || expectedStatus == null) {
            return actualStatus == expectedStatus;
        }
        return actualStatus.equalsIgnoreCase(expectedStatus);
    }

    private boolean lineNumberMatches(int actualLineNumber, int expectedLineNumber) {
        return actualLineNumber == expectedLineNumber;
    }
}
