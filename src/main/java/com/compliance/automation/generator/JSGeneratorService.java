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

        log.info("Generated JavaScript functions for {} rules", result.size());

        return result;
    }

    private String generateOrRetrieveCheckFunction(Rule rule) {
        String cacheKey = rule.getRuleId();

        // Check cache first
        if (jsCodeCache.containsKey(cacheKey)) {
            log.debug("Using cached generated JS for ruleId={}", cacheKey);
            return jsCodeCache.get(cacheKey);
        }

        // Generate if not cached
        String jsCode = ollamaService.generateCheckFunction(rule.getExpectedCommand());
        log.debug("Generated JS for ruleId={}: {}", cacheKey, truncate(jsCode));

        // Store in cache
        jsCodeCache.put(cacheKey, jsCode);

        return jsCode;
    }

    public void clearCache() {
        jsCodeCache.clear();
        log.info("JS generation cache cleared");
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
