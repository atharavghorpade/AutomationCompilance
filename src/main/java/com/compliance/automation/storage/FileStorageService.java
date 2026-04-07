package com.compliance.automation.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.stereotype.Service;

@Service
public class FileStorageService {

	private static final String JS_OUTPUT_DIR = "output/js";
	private static final String REPORT_OUTPUT_DIR = "output/reports";

	public Path saveJavaScript(String ruleId, String jsCode) {
		String fileName = ruleId + ".js";
		String formattedJsCode = formatJavaScriptCode(jsCode);
		return saveFile(JS_OUTPUT_DIR, fileName, formattedJsCode);
	}

	public Path saveReport(String fileName, String reportContent) {
		return saveFile(REPORT_OUTPUT_DIR, fileName, reportContent);
	}

	private Path saveFile(String directory, String fileName, String content) {
		try {
			Path outputDirectory = Paths.get(directory);
			Files.createDirectories(outputDirectory);

			Path filePath = outputDirectory.resolve(fileName);
			Files.writeString(filePath, content, StandardCharsets.UTF_8);
			return filePath;
		} catch (IOException exception) {
			throw new RuntimeException("Failed to save file: " + fileName, exception);
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
