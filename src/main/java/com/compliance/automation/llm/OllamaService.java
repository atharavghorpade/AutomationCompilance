package com.compliance.automation.llm;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OllamaService {

    private static final String MODEL = "llama3";
    private static final String PROMPT_TEMPLATE = """
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

        Map<String, Object> request = new HashMap<>();
        request.put("model", MODEL);
        request.put("prompt", prompt);
        request.put("stream", false);

        try {
            String response = restTemplate.postForObject(fullUrl, request, String.class);
            return extractGeneratedText(response);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to generate JS from Ollama: " + exception.getMessage(), exception);
        }
    }

    private String extractGeneratedText(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String generated = root.get("response").asText();
            return cleanGeneratedCode(generated);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to parse Ollama response: " + exception.getMessage(), exception);
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
            throw new RuntimeException("Generated code does not contain function check(config)");
        }
        
        return code;
    }
}
