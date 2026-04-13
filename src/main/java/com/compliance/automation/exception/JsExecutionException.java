package com.compliance.automation.exception;

public class JsExecutionException extends RuntimeException {

    public JsExecutionException(String message) {
        super(message);
    }

    public JsExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
