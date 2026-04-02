package com.compliance.automation.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {

    private String ruleId;
    private String status;
    private String evidence;
    private int lineNumber;
}
