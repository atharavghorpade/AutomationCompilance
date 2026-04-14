package com.compliance.automation.generator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.compliance.automation.exception.LlmProcessingException;
import com.compliance.automation.formatter.CISFormatter;
import com.compliance.automation.formatter.STIGFormatter;
import com.compliance.automation.llm.OllamaService;
import com.compliance.automation.model.ComplianceType;
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
    private static final String MATCH_TOKEN_DOUBLE_QUOTE = "lines[i].includes(\"";
    private static final String MATCH_TOKEN_SINGLE_QUOTE = "lines[i].includes('";
    private static final String PASS_LINE_NUMBER_TOKEN = "linenumber: i + 1";
    private static final String FAIL_EVIDENCE_TOKEN = "evidence: \"not found\"";
    private static final String FAIL_LINE_NUMBER_TOKEN = "linenumber: -1";

    private final OllamaService ollamaService;
    private final CISFormatter cisFormatter;
    private final STIGFormatter stigFormatter;
    private final Map<String, String> jsCodeCache;

    public JSGeneratorService(OllamaService ollamaService, CISFormatter cisFormatter, STIGFormatter stigFormatter) {
        this.ollamaService = ollamaService;
        this.cisFormatter = cisFormatter;
        this.stigFormatter = stigFormatter;
        this.jsCodeCache = new ConcurrentHashMap<>();
    }

    public Map<String, String> generateCheckFunctions(List<Rule> rules, ComplianceType complianceType) {
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
                String jsCode = generateOrRetrieveCheckFunction(rule, complianceType);
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

    public Map<String, String> generateCheckFunctions(List<Rule> rules) {
        return generateCheckFunctions(rules, ComplianceType.CIS);
    }

    private String generateOrRetrieveCheckFunction(Rule rule, ComplianceType complianceType) {
        String cacheKey = rule.getRuleId();

        if (jsCodeCache.containsKey(cacheKey)) {
            String cachedJs = jsCodeCache.get(cacheKey);
            if (isValidJavaScript(cachedJs, complianceType)) {
                log.debug("Using cached JavaScript for ruleId={}", cacheKey);
                return cachedJs;
            }

            log.warn("Cached JavaScript is invalid for ruleId={}; regenerating", cacheKey);
            jsCodeCache.remove(cacheKey);
        }

        String jsCode = formatGeneratedJavaScript(generateValidJavaScript(rule), complianceType);
        log.debug("Validated JS for ruleId={}: {}", cacheKey, truncate(jsCode));
        jsCodeCache.put(cacheKey, jsCode);
        return jsCode;
    }

    private String generateValidJavaScript(Rule rule) {
        String firstAttempt = ollamaService.generateCheckFunction(rule.getExpectedCommand());
        if (isValidJavaScript(firstAttempt, ComplianceType.CIS)) {
            return firstAttempt;
        }

        log.warn("Generated JS is invalid for ruleId={}; retrying once", rule.getRuleId());

        String secondAttempt = ollamaService.generateCheckFunction(rule.getExpectedCommand());
        if (isValidJavaScript(secondAttempt, ComplianceType.CIS)) {
            return secondAttempt;
        }

        log.error("Generated JS failed validation after retry for ruleId={}", rule.getRuleId());
        throw new IllegalStateException(
                "Generated JavaScript is invalid for ruleId=" + rule.getRuleId()
                        + ". Required tokens: 'function check' and 'status'.");
    }

    private String formatGeneratedJavaScript(String jsCode, ComplianceType complianceType) {
        if (complianceType == ComplianceType.STIG) {
            return stigFormatter.format(jsCode);
        }

        return cisFormatter.format(jsCode);
    }

    private boolean isValidJavaScript(String jsCode, ComplianceType complianceType) {
        if (jsCode == null || jsCode.isBlank()) {
            return false;
        }

        String normalized = jsCode.toLowerCase();
        if (complianceType == ComplianceType.STIG) {
            return normalized.contains("var result = {")
                    && normalized.contains("findingdetails")
                    && normalized.contains("line: -1")
                    && normalized.contains("result.status = \"pass\"")
                    && normalized.contains("result.status = \"fail\"")
                    && (normalized.contains(LINE_SPLIT_TOKEN_DOUBLE_QUOTE)
                        || normalized.contains(LINE_SPLIT_TOKEN_SINGLE_QUOTE))
                    && (normalized.contains(MATCH_TOKEN_DOUBLE_QUOTE)
                        || normalized.contains(MATCH_TOKEN_SINGLE_QUOTE));
        }

        return normalized.contains(CHECK_FUNCTION_SIGNATURE)
                && normalized.contains(STATUS_KEYWORD)
                && (normalized.contains(LINE_SPLIT_TOKEN_DOUBLE_QUOTE)
                    || normalized.contains(LINE_SPLIT_TOKEN_SINGLE_QUOTE))
                && (normalized.contains(MATCH_TOKEN_DOUBLE_QUOTE)
                    || normalized.contains(MATCH_TOKEN_SINGLE_QUOTE))
                && normalized.contains(PASS_LINE_NUMBER_TOKEN)
                && normalized.contains(FAIL_EVIDENCE_TOKEN)
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