package com.compliance.automation.executor;

import com.compliance.automation.model.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.springframework.stereotype.Service;

@Service
public class JSExecutor {

    private static final Logger log = LoggerFactory.getLogger(JSExecutor.class);
    private static final int MAX_EVIDENCE_LOG_CHARS = 500;
    private static final String CHECK_FUNCTION_NAME = "check";
    private static final String STATUS_ERROR = "ERROR";

    private final ObjectMapper objectMapper;

    public JSExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Result execute(String jsCode, String config, String ruleId) {
        log.debug("Executing generated JS for ruleId={} (jsSize={}, configSize={})",
                ruleId,
                jsCode == null ? 0 : jsCode.length(),
                config == null ? 0 : config.length());

        try (Context context = Context.newBuilder("js")
                .allowAllAccess(false)
                .build()) {
            if (jsCode == null || jsCode.isBlank()) {
                log.error("Invalid JS for ruleId={}: code is empty", ruleId);
                return buildErrorResult(ruleId, "Invalid JavaScript: code is empty");
            }

            context.eval("js", jsCode);

            Value checkFunction = context.getBindings("js").getMember(CHECK_FUNCTION_NAME);
            if (checkFunction == null || !checkFunction.canExecute()) {
                log.error("Execution failed for ruleId={} because check(config) was not found or not executable", ruleId);
                return buildErrorResult(ruleId, "Missing executable check(config) function");
            }

            String safeConfig = config == null ? "" : config;
            Value executionResult = checkFunction.execute(safeConfig);

            Result result = new Result(
                    ruleId,
                    extractString(executionResult, "status"),
                    extractString(executionResult, "evidence"),
                    extractInt(executionResult, "lineNumber"));

            if ("PASS".equalsIgnoreCase(result.getStatus())) {
                log.debug("Execution result for ruleId={}: status={}, lineNumber={}",
                        ruleId,
                        result.getStatus(),
                        result.getLineNumber());
            } else {
                log.info("Execution result for ruleId={}: status={}, lineNumber={}, evidence={}",
                        ruleId,
                        result.getStatus(),
                        result.getLineNumber(),
                        truncate(result.getEvidence()));
            }
            return result;
        } catch (PolyglotException exception) {
            if (exception.isSyntaxError()) {
                log.error("Syntax error while executing JS for ruleId={}: {}", ruleId, exception.getMessage(), exception);
                return buildErrorResult(ruleId, "Invalid JavaScript: " + exception.getMessage());
            }
            log.error("Runtime error while executing JS for ruleId={}: {}", ruleId, exception.getMessage(), exception);
            return buildErrorResult(ruleId, "Runtime error: " + exception.getMessage());
    }

    private String extractString(Value value, String memberName) {
        if (value == null || !value.hasMember(memberName) || value.getMember(memberName).isNull()) {
            return null;
        }
        return value.getMember(memberName).asString();
    }

    private int extractInt(Value value, String memberName) {
        if (value == null || !value.hasMember(memberName) || value.getMember(memberName).isNull()) {
            return -1;
        }

        Value member = value.getMember(memberName);
        if (member.fitsInInt()) {
            return member.asInt();
        }
        if (member.fitsInLong()) {
            return (int) member.asLong();
        }
        return -1;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= MAX_EVIDENCE_LOG_CHARS) {
            return value;
        }
        return value.substring(0, MAX_EVIDENCE_LOG_CHARS) + "... [truncated]";
    }

    private Result buildErrorResult(String ruleId, String message) {
        return new Result(ruleId, STATUS_ERROR, message, -1);
    }
}
