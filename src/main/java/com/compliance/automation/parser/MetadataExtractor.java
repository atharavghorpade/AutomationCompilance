package com.compliance.automation.parser;

import java.util.ArrayList;
import java.util.List;

import com.compliance.automation.model.Rule;
import org.springframework.stereotype.Service;

@Service
public class MetadataExtractor {

    public List<Rule> extractRules(String source) {
        return extractRulesFromPDF(source);
    }

    private List<Rule> extractRulesFromPDF(String pdfPath) {
        // TODO: Implement PDF parsing logic here
        // For now, return hardcoded rules
        return getHardcodedRules();
    }

    private List<Rule> getHardcodedRules() {
        List<Rule> rules = new ArrayList<>();

        rules.add(new Rule(
                "1.1.1",
                "Ensure aaa new-model is configured",
                "aaa new-model",
                "AAA"));

        rules.add(new Rule(
                "1.2.1",
                "Verify ssh protocol version is set to 2",
                "ip ssh version 2",
                "SSH"));

        rules.add(new Rule(
                "2.1.1",
                "Ensure NTP is configured",
                "ntp server",
                "NTP"));

        return rules;
    }

    public List<Rule> extractRulesFromFile(String filePath) {
        // Extendable method for future PDF parsing implementation
        // This method can be overridden or extended with actual PDF parsing logic
        if (filePath == null || filePath.isEmpty()) {
            return getHardcodedRules();
        }

        if (filePath.endsWith(".pdf")) {
            return extractRulesFromPDF(filePath);
        }

        // Fallback to hardcoded rules
        return getHardcodedRules();
    }
}
