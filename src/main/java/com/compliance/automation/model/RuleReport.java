package com.compliance.automation.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleReport {

    private String ruleId;
    private String actualStatus;
    private String expectedStatus;
    private int lineNumber;
    private String validation;
}