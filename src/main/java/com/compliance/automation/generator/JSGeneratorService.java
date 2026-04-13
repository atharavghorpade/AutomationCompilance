package com.compliance.automation.generator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.compliance.automation.exception.LlmProcessingException;
import com.compliance.automation.llm.OllamaService;
import com.compliance.automation.model.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JSGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(JSGeneratorService.class);
    private static final int MAX_LOG_CHARS = 800;
    private static final String CHECK_FUNCTION_SIGNATURE = "function check";
    private static final String STATUS_KEYWORD = "status";
    private static final String LINE_SPLIT_TOKEN_DOUBLE_QUOTE = "split(\"\\\\n\")";
    private static final String LINE_SPLIT_TOKEN_SINGLE_QUOTE = "split('\\\\n')";
    private static final String MATCH_TOKEN = "includes(expectedcommand)";
    private static final String PASS_LINE_NUMBER_TOKEN = "linenumber: i + 1";
    private static final String FAIL_LINE_NUMBER_TOKEN = "linenumber: -1";

    private final OllamaService ollamaService;
    private final Map<String, String> jsCodeCache;

    public JSGeneratorService(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
        this.jsCodeCache = new ConcurrentHashMap<>();
    }

    public Map<String, String> generateCheckFunctions(List<Rule> rules) {
        Map<String, String> result = new LinkedHashMap<>();
        Exception lastGenerationError = null;

        if (rules == null || rules.isEmpty()) {
            log.warn("No rules provided for JavaScript generation");
            return result;
        }

        log.info("Generating JavaScript for {} rules", rules.size());

        for (Rule rule : rules) {
            if (rule == null || rule.getRuleId() == null || rule.getRuleId().isBlank()) {
                log.warn("Skipping invalid rule entry during JS generation: {}", rule);
                continue;
            }

            try {
                String jsCode = generateOrRetrieveCheckFunction(rule);
                if (jsCode == null || jsCode.isBlank()) {
                    log.warn("No JavaScript generated for ruleId={}", rule.getRuleId());
                    continue;
                }

                result.put(rule.getRuleId(), jsCode);
                log.debug("Generated JavaScript for ruleId={}", rule.getRuleId());
            } catch (Exception exception) {
                lastGenerationError = exception;
                log.warn("Failed to generate JavaScript for ruleId={}; continuing with next rule", rule.getRuleId(), exception);
            }
        }

        if (result.isEmpty() && lastGenerationError != null) {
            throw new LlmProcessingException("Unable to generate JavaScript checks from LLM for the provided rules.",
                    lastGenerationError);
        }

        log.info("Completed JavaScript generation: {} successful out of {} rules", result.size(), rules.size());
        return result;
    }

    private String generateOrRetrieveCheckFunction(Rule rule) {
        String cacheKey = rule.getRuleId();

        if (jsCodeCache.containsKey(cacheKey)) {
            String cachedJs = jsCodeCache.get(cacheKey);
            if (isValidJavaScript(cachedJs)) {
                log.debug("Using cached JavaScript for ruleId={}", cacheKey);
                return cachedJs;
            }

            log.warn("Cached JavaScript is invalid for ruleId={}; regenerating", cacheKey);
            jsCodeCache.remove(cacheKey);
        }

        String jsCode = generateValidJavaScript(rule);
        log.debug("Validated JS for ruleId={}: {}", cacheKey, truncate(jsCode));
        jsCodeCache.put(cacheKey, jsCode);
        return jsCode;
    }

    private String generateValidJavaScript(Rule rule) {
        String firstAttempt = ollamaService.generateCheckFunction(rule.getExpectedCommand());
        if (isValidJavaScript(firstAttempt)) {
            return firstAttempt;
        }

        log.warn("Generated JS is invalid for ruleId={}; retrying once", rule.getRuleId());

        String secondAttempt = ollamaService.generateCheckFunction(rule.getExpectedCommand());
        if (isValidJavaScript(secondAttempt)) {
            return secondAttempt;
        }

        log.error("Generated JS failed validation after retry for ruleId={}", rule.getRuleId());
        throw new IllegalStateException(
                "Generated JavaScript is invalid for ruleId=" + rule.getRuleId()
                        + ". Required tokens: 'function check' and 'status'.");
    }

    private boolean isValidJavaScript(String jsCode) {
        if (jsCode == null || jsCode.isBlank()) {
            return false;
        }

        String normalized = jsCode.toLowerCase();
        return normalized.contains(CHECK_FUNCTION_SIGNATURE)
                && normalized.contains(STATUS_KEYWORD)
            && (normalized.contains(LINE_SPLIT_TOKEN_DOUBLE_QUOTE)
                || normalized.contains(LINE_SPLIT_TOKEN_SINGLE_QUOTE))
                && normalized.contains(MATCH_TOKEN)
                && normalized.contains(PASS_LINE_NUMBER_TOKEN)
                && normalized.contains(FAIL_LINE_NUMBER_TOKEN);
    }

    public void clearCache() {
        jsCodeCache.clear();
        log.info("JavaScript generation cache cleared");
    }

    public int getCacheSize() {
        return jsCodeCache.size();
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= MAX_LOG_CHARS) {
            return value;
        }
        return value.substring(0, MAX_LOG_CHARS) + "... [truncated]";
    }
}