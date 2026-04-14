package com.compliance.automation.formatter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class CISFormatter {

	private static final Pattern DIRECT_INCLUDES_PATTERN = Pattern.compile("includes\\(\\s*[\\\"'](.+?)[\\\"']\\s*\\)");
	private static final Pattern EXPECTED_VAR_PATTERN = Pattern.compile("const\\s+expectedCommand\\s*=\\s*[\\\"'](.+?)[\\\"']\\s*;");

	public String format(String generatedJs) {
		if (generatedJs == null || generatedJs.isBlank()) {
			throw new IllegalArgumentException("Generated JavaScript must not be empty");
		}

		String normalized = normalize(generatedJs);
		String expectedCommand = extractExpectedCommand(normalized);
		String escapedExpectedCommand = escapeForDoubleQuotedJsString(expectedCommand);

		String lineSeparator = System.lineSeparator();
		return "function check(config) {" + lineSeparator
				+ "    const lines = config.split(\\\"\\n\\\");" + lineSeparator
				+ lineSeparator
				+ "    for (let i = 0; i < lines.length; i++) {" + lineSeparator
				+ "        if (lines[i].includes(\\\"" + escapedExpectedCommand + "\\\")) {" + lineSeparator
				+ "            return {" + lineSeparator
				+ "                status: \\\"PASS\\\"," + lineSeparator
				+ "                evidence: lines[i]," + lineSeparator
				+ "                lineNumber: i + 1" + lineSeparator
				+ "            };" + lineSeparator
				+ "        }" + lineSeparator
				+ "    }" + lineSeparator
				+ lineSeparator
				+ "    return {" + lineSeparator
				+ "        status: \\\"FAIL\\\"," + lineSeparator
				+ "        evidence: \\\"Not found\\\"," + lineSeparator
				+ "        lineNumber: -1" + lineSeparator
				+ "    };" + lineSeparator
				+ "}" + lineSeparator;
	}

	private String extractExpectedCommand(String jsCode) {
		Matcher directMatcher = DIRECT_INCLUDES_PATTERN.matcher(jsCode);
		if (directMatcher.find()) {
			return directMatcher.group(1);
		}

		Matcher expectedVarMatcher = EXPECTED_VAR_PATTERN.matcher(jsCode);
		if (expectedVarMatcher.find()) {
			return expectedVarMatcher.group(1);
		}

		throw new IllegalArgumentException("Generated JavaScript must contain includes(\"EXPECTED_COMMAND\") logic");
	}

	private String escapeForDoubleQuotedJsString(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private String normalize(String text) {
		String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
		normalized = normalized.replace("```javascript", "")
				.replace("```js", "")
				.replace("```", "");
		return normalized.trim();
	}
}
