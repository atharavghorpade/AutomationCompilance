package com.compliance.automation.llm;

import java.util.HashMap;
import java.util.Map;

import com.compliance.automation.exception.LlmProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OllamaService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);
    private static final int MAX_LOG_CHARS = 1200;
    private static final String MODEL = "llama3";
    private static final String PROMPT_TEMPLATE = """
Output ONLY JavaScript.
No explanation.
No markdown.

function check(config) {
    const lines = config.split("\\n");

    for (let i = 0; i < lines.length; i++) {
        if (lines[i].includes("%s")) {
            return {
                status: "PASS",
                evidence: lines[i],
                lineNumber: i + 1
            };
        }
    }

    return {
        status: "FAIL",
        evidence: "Not found",
        lineNumber: -1
    };
}
""";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String ollamaUrl;

    public OllamaService(RestTemplate restTemplate, ObjectMapper objectMapper,
            @Value("${ollama.url:http://localhost:11434}") String ollamaUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.ollamaUrl = ollamaUrl;
    }

    public String generateCheckFunction(String expectedCommand) {
        String prompt = String.format(PROMPT_TEMPLATE, expectedCommand);
        String fullUrl = ollamaUrl + "/api/generate";
        log.info("LLM request started (model={}, endpoint={}, expectedCommand={})",
            MODEL,
            fullUrl,
            expectedCommand);
        log.debug("LLM request prompt (trimmed): {}", truncate(prompt));

        Map<String, Object> request = new HashMap<>();
        request.put("model", MODEL);
        request.put("prompt", prompt);
        request.put("stream", false);

        try {
            String response = restTemplate.postForObject(fullUrl, request, String.class);
            log.debug("LLM raw response (trimmed): {}", truncate(response));
            String generatedCode = extractGeneratedText(response);
            log.info("LLM request completed (generatedChars={})", generatedCode == null ? 0 : generatedCode.length());
            log.debug("LLM generated JS (trimmed): {}", truncate(generatedCode));
            return generatedCode;
        } catch (Exception exception) {
            log.error("Failed to generate JS from Ollama for expectedCommand={}", expectedCommand, exception);
            throw new LlmProcessingException("Failed to generate JS from Ollama.", exception);
        }
    }

    public String generateCheckFunctionWithPrompt(String customPrompt) {
        String fullUrl = ollamaUrl + "/api/generate";
        log.info("LLM retry request started (model={}, endpoint={})", MODEL, fullUrl);
        log.debug("LLM custom prompt (trimmed): {}", truncate(customPrompt));

        Map<String, Object> request = new HashMap<>();
        request.put("model", MODEL);
        request.put("prompt", customPrompt);
        request.put("stream", false);

        try {
            String response = restTemplate.postForObject(fullUrl, request, String.class);
            log.debug("LLM raw response (trimmed): {}", truncate(response));
            String generatedCode = extractGeneratedText(response);
            log.info("LLM retry request completed (generatedChars={})", generatedCode == null ? 0 : generatedCode.length());
            log.debug("LLM generated JS from retry prompt (trimmed): {}", truncate(generatedCode));
            return generatedCode;
        } catch (Exception exception) {
            log.error("Failed to generate JS from Ollama with custom prompt", exception);
            throw new LlmProcessingException("Failed to generate JS from Ollama using retry prompt.", exception);
        }
    }

    private String extractGeneratedText(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String generated = root.get("response").asText();
            return cleanGeneratedCode(generated);
        } catch (Exception exception) {
            throw new LlmProcessingException("Failed to parse Ollama response.", exception);
        }
    }

    private String cleanGeneratedCode(String code) {
        // Remove markdown code blocks if present
        code = code.replaceAll("```javascript\\n?", "");
        code = code.replaceAll("```js\\n?", "");
        code = code.replaceAll("```\\n?", "");
        
        // Trim whitespace and ensure it starts with function
        code = code.trim();
        
        if (!code.startsWith("function check")) {
            throw new LlmProcessingException("Generated code does not contain function check(config)");
        }

        validateLineDetectionLogic(code);
        
        return code;
    }

    private void validateLineDetectionLogic(String code) {
        String normalized = code.replace("\r\n", "\n").replace('\r', '\n').toLowerCase();

        if (!normalized.contains("const lines = config.split(\"\\\\n\")")
                && !normalized.contains("const lines=config.split(\"\\\\n\")")
                && !normalized.contains("const lines = config.split('\\\\n')")
                && !normalized.contains("const lines=config.split('\\\\n')")) {
            throw new LlmProcessingException("Generated code must split config into lines using config.split(\"\\n\").");
        }

        if (!normalized.contains("for (let i = 0; i < lines.length; i++)")
                && !normalized.contains("for(let i=0;i<lines.length;i++)")) {
            throw new LlmProcessingException("Generated code must iterate over lines using index-based for loop.");
        }

        if (!normalized.contains("lines[i].includes(\"") && !normalized.contains("lines[i].includes('") ) {
            throw new LlmProcessingException("Generated code must use lines[i].includes(\"EXPECTED_COMMAND\") matching logic.");
        }

        if (!normalized.contains("linenumber: i + 1") && !normalized.contains("linenumber:i+1")) {
            throw new LlmProcessingException("Generated code must set PASS lineNumber to i + 1.");
        }

        if (!normalized.contains("evidence: \"not found\"") && !normalized.contains("evidence:\"not found\"")) {
            throw new LlmProcessingException("Generated code must set FAIL evidence to \"Not found\".");
        }

        if (!normalized.contains("linenumber: -1") && !normalized.contains("linenumber:-1")) {
            throw new LlmProcessingException("Generated code must set FAIL lineNumber to -1.");
        }
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
