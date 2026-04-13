package com.compliance.automation.model;

import java.util.Locale;

public enum ComplianceType {
    CIS,
    STIG;

    public static ComplianceType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("type is required and must be either CIS or STIG.");
        }

        try {
            return ComplianceType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("type must be either CIS or STIG.", exception);
        }
    }
}
