package com.compliance.automation.retry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.compliance.automation.executor.JSExecutor;
import com.compliance.automation.llm.OllamaService;
import com.compliance.automation.model.Result;
import org.springframework.stereotype.Service;

@Service
public class RetryEngine {

    private static final int MAX_RETRIES = 2;
    private static final String IMPROVED_PROMPT = """
Previous result was wrong. Fix logic.

Generate JavaScript function.

Rules:
- Function name: check(config)
- Input: config (string)
- Output:
  { status: "PASS" | "FAIL", evidence: string, lineNumber: number }

Check if config contains:
"%s"

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
            Map<String, String> ruleIdToExpectedCommand) {
        List<Result> retryResults = new ArrayList<>();

        for (String ruleId : failedRuleIds) {
            Result retryResult = retryRule(ruleId, config, ruleIdToExpectedCommand.get(ruleId));
            retryResults.add(retryResult);
        }

        return retryResults;
    }

    private Result retryRule(String ruleId, String config, String expectedCommand) {
        Result result = null;
        int retryCount = 0;

        while (retryCount < MAX_RETRIES) {
            try {
                String improvedJsCode = generateImprovedCode(expectedCommand);
                result = jsExecutor.execute(improvedJsCode, config, ruleId);

                // If we got a successful result, return it
                if (result != null) {
                    return result;
                }
            } catch (Exception exception) {
                // Log and continue to next retry
            }

            retryCount++;
        }

        // Return last result or error result
        if (result != null) {
            return result;
        }

        return new Result(ruleId, "RETRY_FAILED", "Max retries exceeded", -1);
    }

    private String generateImprovedCode(String expectedCommand) {
        return ollamaService.generateCheckFunctionWithPrompt(String.format(IMPROVED_PROMPT, expectedCommand));
    }
}
