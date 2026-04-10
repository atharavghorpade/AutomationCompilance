package com.compliance.automation.loader;

import com.compliance.automation.model.ExpectedResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class ExpectedResultLoader {

    private static final Logger log = LoggerFactory.getLogger(ExpectedResultLoader.class);

    private final ObjectMapper objectMapper;

    public ExpectedResultLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ExpectedResult> load(MultipartFile expectedFile) {
        if (expectedFile == null || expectedFile.isEmpty()) {
            log.warn("Expected result file is missing or empty");
            throw new ExpectedResultLoaderException("Expected results file is missing or empty.");
        }

        String fileName = expectedFile.getOriginalFilename();
        log.info("Loading expected results from file={}", fileName);

        try (InputStream inputStream = expectedFile.getInputStream()) {
            List<ExpectedResult> expectedResults = readExpectedResults(inputStream, fileName);
            log.info("Loaded {} expected results from file={}", expectedResults.size(), fileName);
            return expectedResults;
        } catch (IOException ex) {
            log.error("Failed to read expected results file={}", fileName, ex);
            throw new ExpectedResultLoaderException("Failed to read expected results file.", ex);
        }
    }

    private List<ExpectedResult> readExpectedResults(InputStream inputStream, String sourceName) {
        JavaType expectedResultType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, ExpectedResult.class);

        try {
            return objectMapper.readValue(inputStream, expectedResultType);
        } catch (JsonProcessingException ex) {
            String fileName = sourceName == null ? "expected results" : sourceName;
            log.warn("Invalid JSON in expected results file={}", fileName, ex);
            throw new ExpectedResultLoaderException("Invalid JSON in " + fileName + ".", ex);
        } catch (IOException ex) {
            log.error("Failed parsing expected results file={}", sourceName, ex);
            throw new ExpectedResultLoaderException("Failed to parse expected results.", ex);
        }
    }
}