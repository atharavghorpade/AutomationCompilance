package com.compliance.automation.validator;

import java.util.ArrayList;
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

        // Create a map of expected results by ruleId for quick lookup
        Map<String, ExpectedResult> expectedMap = expectedResults.stream()
                .collect(Collectors.toMap(ExpectedResult::getRuleId, e -> e));

        // Check each actual result against expected
        for (Result actual : actualResults) {
            ExpectedResult expected = expectedMap.get(actual.getRuleId());

            if (expected == null) {
                // No expected result for this rule
                failedRuleIds.add(actual.getRuleId());
                continue;
            }

            // Compare status and lineNumber
            if (!statusMatches(actual.getStatus(), expected.getStatus())
                    || !lineNumberMatches(actual.getLineNumber(), expected.getLineNumber())) {
                failedRuleIds.add(actual.getRuleId());
            }
        }

        // Check for expected results that don't have actual results
        for (ExpectedResult expected : expectedResults) {
            boolean found = actualResults.stream()
                    .anyMatch(r -> r.getRuleId().equals(expected.getRuleId()));

            if (!found) {
                failedRuleIds.add(expected.getRuleId());
            }
        }

        boolean matched = failedRuleIds.isEmpty();
        return new ValidationResult(matched, failedRuleIds);
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
