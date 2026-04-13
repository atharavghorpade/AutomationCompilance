package com.compliance.automation.formatter;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class CISFormatter {

	public String format(String generatedJs) {
		if (generatedJs == null || generatedJs.isBlank()) {
			throw new IllegalArgumentException("Generated JavaScript must not be empty");
		}

		String normalized = normalize(generatedJs);
		String body = extractBody(normalized);

		StringBuilder formatted = new StringBuilder();
		formatted.append("function check(config) {").append(System.lineSeparator());

		if (!body.isBlank()) {
			List<String> lines = splitLines(body);
			for (String line : lines) {
				if (line.isBlank()) {
					formatted.append(System.lineSeparator());
				} else {
					formatted.append("    ").append(line).append(System.lineSeparator());
				}
			}
		}

		formatted.append("}").append(System.lineSeparator());
		return formatted.toString();
	}

	private String extractBody(String jsCode) {
		String signature = "function check(config)";
		int signatureIndex = jsCode.indexOf(signature);

		if (signatureIndex < 0) {
			return trimOuterBraces(jsCode);
		}

		int openBrace = jsCode.indexOf('{', signatureIndex);
		int closeBrace = jsCode.lastIndexOf('}');
		if (openBrace < 0 || closeBrace <= openBrace) {
			return trimOuterBraces(jsCode);
		}

		return jsCode.substring(openBrace + 1, closeBrace).trim();
	}

	private String trimOuterBraces(String text) {
		String trimmed = text.trim();
		if (trimmed.startsWith("{") && trimmed.endsWith("}") && trimmed.length() > 2) {
			return trimmed.substring(1, trimmed.length() - 1).trim();
		}
		return trimmed;
	}

	private String normalize(String text) {
		String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
		normalized = normalized.replace("```javascript", "")
				.replace("```js", "")
				.replace("```", "");
		return normalized.trim();
	}

	private List<String> splitLines(String text) {
		String[] rawLines = text.split("\\n", -1);
		List<String> lines = new ArrayList<>(rawLines.length);
		for (String rawLine : rawLines) {
			lines.add(rawLine == null ? "" : rawLine.strip());
		}
		return lines;
	}
}
