package com.compliance.automation.loader;

public class ExpectedResultLoaderException extends RuntimeException {

    public ExpectedResultLoaderException(String message) {
        super(message);
    }

    public ExpectedResultLoaderException(String message, Throwable cause) {
        super(message, cause);
    }
}