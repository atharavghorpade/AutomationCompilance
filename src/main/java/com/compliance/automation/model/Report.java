package com.compliance.automation.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    private List<Result> results;
    private int total;
    private int passed;
    private int failed;
}
