package com.compliance.automation.retry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.compliance.automation.executor.JSExecutor;
import com.compliance.automation.llm.OllamaService;
import com.compliance.automation.model.ExpectedResult;
import com.compliance.automation.model.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RetryEngine {

    private static final Logger log = LoggerFactory.getLogger(RetryEngine.class);
    private static final int MAX_RETRIES = 2;
    private static final String IMPROVED_PROMPT = """
Previous result was incorrect.
Expected:
status: %s
lineNumber: %d
Fix the logic.

Generate JavaScript function.

Rules:
- Function name: check(config)
- Input: config (raw multiline string)
- Output:
  { status: "PASS" | "FAIL", evidence: string, lineNumber: number }

Mandatory matching logic:
- const lines = config.split("\\n");
- for (let i = 0; i < lines.length; i++) { ... }
- if (lines[i].includes(expectedCommand)) return PASS with lineNumber: i + 1
- if not found return FAIL with lineNumber: -1

Return ONLY JavaScript code.
Do not explain.
""";

    private final OllamaService ollamaService;
    private final JSExecutor jsExecutor;

    public RetryEngine(OllamaService ollamaService, JSExecutor jsExecutor) {
        this.ollamaService = ollamaService;
        this.jsExecutor = jsExecutor;
    }

    public List<Result> retryFailedRules(List<String> failedRuleIds, String config,
            Map<String, ExpectedResult> expectedByRuleId) {
        List<Result> retryResults = new ArrayList<>();

        if (failedRuleIds == null || failedRuleIds.isEmpty()) {
            log.info("No failed rules provided for retry");
            return retryResults;
        }

        log.info("Retrying {} failed rules with maxRetries={}", failedRuleIds.size(), MAX_RETRIES);

        for (String ruleId : failedRuleIds) {
            ExpectedResult expectedResult = expectedByRuleId == null ? null : expectedByRuleId.get(ruleId);
            Result retryResult = retryRule(ruleId, config, expectedResult);
            retryResults.add(retryResult);
        }

        return retryResults;
    }

    private Result retryRule(String ruleId, String config, ExpectedResult expectedResult) {
        if (expectedResult == null) {
            log.warn("Skipping retry for ruleId={} because expected result is missing", ruleId);
            return new Result(ruleId, "ERROR", "Missing expected result for retry", -1);
        }

        Result lastResult = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.info("Retry attempt {}/{} for ruleId={}", attempt, MAX_RETRIES, ruleId);
                String improvedJsCode = generateImprovedCode(expectedResult.getStatus(), expectedResult.getLineNumber());
                lastResult = jsExecutor.execute(improvedJsCode, config, ruleId);

                if (lastResult != null) {
                    log.info("Retry attempt {}/{} completed for ruleId={} with status={} and lineNumber={}",
                            attempt,
                            MAX_RETRIES,
                            ruleId,
                            lastResult.getStatus(),
                            lastResult.getLineNumber());

                    if (!"ERROR".equalsIgnoreCase(lastResult.getStatus())) {
                        return lastResult;
                    }
                }
            } catch (Exception exception) {
                log.warn("Retry attempt {}/{} failed for ruleId={}", attempt, MAX_RETRIES, ruleId, exception);
                lastResult = new Result(ruleId, "ERROR", "Retry failed: " + exception.getMessage(), -1);
            }
        }

        if (lastResult != null) {
            log.warn("Retry exhausted for ruleId={} returning last result status={}", ruleId, lastResult.getStatus());
            return lastResult;
        }

        log.error("Retry exhausted for ruleId={} with no result produced", ruleId);
        return new Result(ruleId, "ERROR", "Max retries exceeded", -1);
    }

    private String generateImprovedCode(String expectedStatus, int expectedLine) {
        return ollamaService.generateCheckFunctionWithPrompt(
                String.format(IMPROVED_PROMPT, expectedStatus, expectedLine));
    }
}
