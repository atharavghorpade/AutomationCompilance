package com.compliance.automation.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.compliance.automation.exception.FileProcessingException;
import com.compliance.automation.model.Rule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FileStorageService {

	private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
	private static final String JS_OUTPUT_DIR = "output/js";
	private static final String REPORT_OUTPUT_DIR = "output/reports";
	private static final String METADATA_OUTPUT_DIR = "output/metadata";
	private static final String METADATA_FILE_NAME = "metadata.json";

	private final ObjectMapper objectMapper;

	public FileStorageService(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public Path saveJavaScript(String ruleId, String jsCode) {
		String fileName = ruleId + ".js";
		String formattedJsCode = formatJavaScriptCode(jsCode);
		return saveFile(JS_OUTPUT_DIR, fileName, formattedJsCode);
	}

	public Path saveReport(String fileName, String reportContent) {
		return saveFile(REPORT_OUTPUT_DIR, fileName, reportContent);
	}

	public Path saveMetadata(List<Rule> rules) {
		List<Rule> safeRules = rules == null ? List.of() : rules;
		log.info("Saving metadata for {} rules to {}/{}", safeRules.size(), METADATA_OUTPUT_DIR, METADATA_FILE_NAME);

		try {
			String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(safeRules);
			Path savedPath = saveFile(METADATA_OUTPUT_DIR, METADATA_FILE_NAME, prettyJson + System.lineSeparator());
			log.info("Metadata saved successfully at {}", savedPath.toAbsolutePath());
			return savedPath;
		} catch (IOException exception) {
			log.error("Failed to save metadata JSON", exception);
			throw new FileProcessingException("Failed to save metadata JSON.", exception);
		}
	}

	private Path saveFile(String directory, String fileName, String content) {
		try {
			Path outputDirectory = Paths.get(directory);
			Files.createDirectories(outputDirectory);

			Path filePath = outputDirectory.resolve(fileName);
			Files.writeString(filePath, content, StandardCharsets.UTF_8);
			return filePath;
		} catch (IOException exception) {
			log.error("Failed to save file {} in directory {}", fileName, directory, exception);
			throw new FileProcessingException("Failed to save file: " + fileName, exception);
		}
	}

	private String formatJavaScriptCode(String jsCode) {
		if (jsCode == null) {
			return "";
		}

		String normalized = jsCode
				.replace("\r\n", "\n")
				.replace("\r", "\n")
				.trim();

		if (normalized.isEmpty()) {
			return "";
		}

		return normalized + System.lineSeparator();
	}
}
