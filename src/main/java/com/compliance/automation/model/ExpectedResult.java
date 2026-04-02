package com.compliance.automation.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpectedResult {

    private String ruleId;
    private String status;
    private int lineNumber;
}
