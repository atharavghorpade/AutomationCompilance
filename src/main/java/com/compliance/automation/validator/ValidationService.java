package com.compliance.automation.validator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.compliance.automation.model.ExpectedResult;
import com.compliance.automation.model.Result;
import com.compliance.automation.model.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    public ValidationResult validate(List<Result> actualResults, List<ExpectedResult> expectedResults) {
        List<String> failedRuleIds = new ArrayList<>();
        Map<String, String> mismatchReasons = new LinkedHashMap<>();

        int actualCount = actualResults == null ? 0 : actualResults.size();
        int expectedCount = expectedResults == null ? 0 : expectedResults.size();
        log.info("Starting validation: actualResults={}, expectedResults={}", actualCount, expectedCount);

        if (actualResults == null || actualResults.isEmpty()) {
            log.warn("No actual results provided for validation");
        }

        if (expectedResults == null || expectedResults.isEmpty()) {
            log.warn("No expected results provided for validation");
        }

        Map<String, ExpectedResult> expectedMap = expectedResults == null ? Map.of() : expectedResults.stream()
                .filter(Objects::nonNull)
                .filter(expected -> expected.getRuleId() != null && !expected.getRuleId().isBlank())
                .collect(Collectors.toMap(ExpectedResult::getRuleId, e -> e, (first, second) -> first, LinkedHashMap::new));

        Map<String, Result> actualMap = actualResults == null ? Map.of() : actualResults.stream()
                .filter(Objects::nonNull)
                .filter(actual -> actual.getRuleId() != null && !actual.getRuleId().isBlank())
                .collect(Collectors.toMap(Result::getRuleId, r -> r, (first, second) -> first, LinkedHashMap::new));

        log.debug("Validation maps prepared: actualKeys={}, expectedKeys={}", actualMap.keySet(), expectedMap.keySet());

        for (Result actual : actualMap.values()) {
            String actualRuleId = actual.getRuleId();
            ExpectedResult expected = expectedMap.get(actualRuleId);

            if (expected == null) {
                addFailure(failedRuleIds, mismatchReasons, actualRuleId,
                        "Missing expected result for ruleId " + actualRuleId);
                continue;
            }

            List<String> mismatches = new ArrayList<>();

            if (!ruleIdMatches(actualRuleId, expected.getRuleId())) {
                mismatches.add("ruleId mismatch: expected=" + expected.getRuleId() + ", actual=" + actualRuleId);
            }

            if (!statusMatches(actual.getStatus(), expected.getStatus())) {
                mismatches.add("status mismatch: expected=" + expected.getStatus() + ", actual=" + actual.getStatus());
            }

            if (!lineNumberMatches(actual.getLineNumber(), expected.getLineNumber())) {
                mismatches.add("lineNumber mismatch: expected=" + expected.getLineNumber() + ", actual=" + actual.getLineNumber());
            }

            if (!mismatches.isEmpty()) {
                String reason = String.join("; ", mismatches);
                log.debug("Rule validation failed for ruleId={}: {}", actualRuleId, reason);
                addFailure(failedRuleIds, mismatchReasons, actualRuleId, reason);
            } else {
                log.debug("Rule validation matched for ruleId={}", actualRuleId);
            }
        }

        for (ExpectedResult expected : expectedResults) {
            if (expected == null || expected.getRuleId() == null || expected.getRuleId().isBlank()) {
                continue;
            }

            if (!actualMap.containsKey(expected.getRuleId())) {
                String reason = "Missing actual result for ruleId " + expected.getRuleId();
                log.debug("Expected rule missing in actual results: {}", reason);
                addFailure(failedRuleIds, mismatchReasons, expected.getRuleId(), reason);
            }
        }

        boolean matched = failedRuleIds.isEmpty();
        if (matched) {
            log.info("Validation passed: all {} rules matched", actualMap.size());
        } else {
            log.warn("Validation completed with {} mismatches", failedRuleIds.size());
            log.debug("Validation mismatch reasons: {}", mismatchReasons);
        }
        return new ValidationResult(matched, failedRuleIds, mismatchReasons);
    }

    private void addFailure(List<String> failedRuleIds, Map<String, String> mismatchReasons,
                            String ruleId, String reason) {
        if (!failedRuleIds.contains(ruleId)) {
            failedRuleIds.add(ruleId);
        }
        mismatchReasons.put(ruleId, reason);
        log.warn("Validation mismatch for ruleId={}: {}", ruleId, reason);
    }

    private boolean ruleIdMatches(String actualRuleId, String expectedRuleId) {
        if (actualRuleId == null || expectedRuleId == null) {
            return actualRuleId == expectedRuleId;
        }
        return actualRuleId.equals(expectedRuleId);
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
