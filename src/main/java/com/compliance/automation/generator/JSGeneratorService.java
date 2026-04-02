package com.compliance.automation.generator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.compliance.automation.llm.OllamaService;
import com.compliance.automation.model.Rule;
import org.springframework.stereotype.Service;

@Service
public class JSGeneratorService {

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

        return result;
    }

    private String generateOrRetrieveCheckFunction(Rule rule) {
        String cacheKey = rule.getRuleId();

        // Check cache first
        if (jsCodeCache.containsKey(cacheKey)) {
            return jsCodeCache.get(cacheKey);
        }

        // Generate if not cached
        String jsCode = ollamaService.generateCheckFunction(rule.getExpectedCommand());

        // Store in cache
        jsCodeCache.put(cacheKey, jsCode);

        return jsCode;
    }

    public void clearCache() {
        jsCodeCache.clear();
    }

    public int getCacheSize() {
        return jsCodeCache.size();
    }
}
