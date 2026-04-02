package com.compliance.automation.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Rule {

    private String ruleId;
    private String description;
    private String expectedCommand;
    private String type;
}
