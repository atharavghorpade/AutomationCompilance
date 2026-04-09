package com.compliance.automation.generator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final String RETURN_KEYWORD = "return";

    private final OllamaService ollamaService;
    private final Map<String, String> jsCodeCache;

    public JSGeneratorService(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
        this.jsCodeCache = new ConcurrentHashMap<>();
    }

    public Map<String, String> generateCheckFunctions(List<Rule> rules) {
        Map<String, String> result = new HashMap<>();

        for (Rule rule : rules) {
            String jsCode = generateOrRetrieveCheckFunction(rule);
            result.put(rule.getRuleId(), jsCode);
        }

        log.info("Generated JavaScript for {} rules", result.size());

        return result;
    }

    private String generateOrRetrieveCheckFunction(Rule rule) {
        String cacheKey = rule.getRuleId();

        // Check cache first
        if (jsCodeCache.containsKey(cacheKey)) {
            log.debug("Using cached JavaScript for ruleId={}", cacheKey);
            String cachedJs = jsCodeCache.get(cacheKey);
            if (isValidJavaScript(cachedJs)) {
                return cachedJs;
            }

            log.warn("Cached JavaScript is invalid for ruleId={}; regenerating", cacheKey);
            jsCodeCache.remove(cacheKey);
        }

        String jsCode = generateValidJavaScript(rule);
        log.debug("Validated JS for ruleId={}: {}", cacheKey, truncate(jsCode));

        // Store in cache
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

        throw new IllegalStateException(
                "Generated JavaScript is invalid for ruleId=" + rule.getRuleId()
                        + ". Required tokens: 'function check' and 'return'.");
    }

    private boolean isValidJavaScript(String jsCode) {
        if (jsCode == null) {
            return false;
        }

        String normalized = jsCode.toLowerCase();
        return normalized.contains(CHECK_FUNCTION_SIGNATURE) && normalized.contains(RETURN_KEYWORD);
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
