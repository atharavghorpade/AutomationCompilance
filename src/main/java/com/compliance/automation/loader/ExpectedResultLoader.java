package com.compliance.automation.loader;

import com.compliance.automation.model.ExpectedResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

@Service
public class ExpectedResultLoader {

    private final ObjectMapper objectMapper;

    public ExpectedResultLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ExpectedResult> load(MultipartFile expectedFile) {
        if (expectedFile == null || expectedFile.isEmpty()) {
            throw new ExpectedResultLoaderException("Expected results file is missing or empty.");
        }

        try (InputStream inputStream = expectedFile.getInputStream()) {
            return readExpectedResults(inputStream, expectedFile.getOriginalFilename());
        } catch (IOException ex) {
            throw new ExpectedResultLoaderException("Failed to read expected results file.", ex);
        }
    }

    public List<ExpectedResult> load(File expectedFile) {
        if (expectedFile == null || !expectedFile.exists()) {
            throw new ExpectedResultLoaderException("Expected results file does not exist.");
        }

        try (InputStream inputStream = Files.newInputStream(expectedFile.toPath())) {
            return readExpectedResults(inputStream, expectedFile.getName());
        } catch (IOException ex) {
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
            throw new ExpectedResultLoaderException("Invalid JSON in " + fileName + ".", ex);
        } catch (IOException ex) {
            throw new ExpectedResultLoaderException("Failed to parse expected results.", ex);
        }
    }
}