package com.compliance.automation.report;

import java.util.List;

import com.compliance.automation.model.Report;
import com.compliance.automation.model.Result;
import org.springframework.stereotype.Service;

@Service
public class ReportGenerator {

	public Report generateReport(List<Result> results) {
		int total = results != null ? results.size() : 0;
		int passed = 0;
		int failed = 0;

		if (results != null) {
			for (Result result : results) {
				if (result != null && "PASS".equalsIgnoreCase(result.getStatus())) {
					passed++;
				} else {
					failed++;
				}
			}
		}

		return new Report(results, total, passed, failed);
	}
}
