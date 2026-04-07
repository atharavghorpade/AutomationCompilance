package com.compliance.automation.model;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    private boolean matched;
    private List<String> failedRuleIds;
    private Map<String, String> mismatchReasons;
}
