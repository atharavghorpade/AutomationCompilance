package com.compliance.automation.executor;

import java.io.IOException;

import com.compliance.automation.model.Result;
import com.fasterxml.jackson.databind.JsonNode;
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
    private static final String STATUS_INVALID_JS = "INVALID_JS";
    private static final String STATUS_MISSING_FUNCTION = "MISSING_FUNCTION";
    private static final String STATUS_RUNTIME_ERROR = "RUNTIME_ERROR";
    private static final String STATUS_INVALID_CONFIG = "INVALID_CONFIG";

    private final ObjectMapper objectMapper;

    public JSExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Result execute(String jsCode, String config, String ruleId) {
        log.debug("Executing JS for ruleId={} with jsSize={} and configSize={}",
                ruleId,
                jsCode == null ? 0 : jsCode.length(),
                config == null ? 0 : config.length());

        try (Context context = Context.newBuilder("js")
                .allowAllAccess(false)
                .build()) {
            context.eval("js", jsCode);

            Value checkFunction = context.getBindings("js").getMember(CHECK_FUNCTION_NAME);
            if (checkFunction == null || !checkFunction.canExecute()) {
                log.warn("Execution failed for ruleId={} because check(config) is missing", ruleId);
                return new Result(ruleId, STATUS_MISSING_FUNCTION, "Missing executable check(config) function", -1);
            }

            Value configValue = parseAndConvertConfig(context, config);
            Value executionResult = checkFunction.execute(configValue);

            Result result = new Result(
                    ruleId,
                    extractString(executionResult, "status"),
                    extractString(executionResult, "evidence"),
                    extractInt(executionResult, "lineNumber"));

            log.info("Execution result for ruleId={}: status={}, lineNumber={}",
                    ruleId,
                    result.getStatus(),
                    result.getLineNumber());
            log.debug("Execution evidence for ruleId={}: {}", ruleId, truncate(result.getEvidence()));
            return result;
        } catch (PolyglotException exception) {
            if (exception.isSyntaxError()) {
                log.warn("Syntax error while executing generated JS for ruleId={}: {}", ruleId, exception.getMessage());
                return new Result(ruleId, STATUS_INVALID_JS, exception.getMessage(), -1);
            }
            log.warn("Runtime error while executing JS for ruleId={}: {}", ruleId, exception.getMessage());
            return new Result(ruleId, STATUS_RUNTIME_ERROR, exception.getMessage(), -1);
        } catch (IllegalArgumentException exception) {
            log.warn("Invalid config for ruleId={}: {}", ruleId, exception.getMessage());
            return new Result(ruleId, STATUS_INVALID_CONFIG, exception.getMessage(), -1);
        }
    }

    private Value parseAndConvertConfig(Context context, String config) {
        try {
            JsonNode jsonNode = objectMapper.readTree(config);
            
            if (jsonNode.isArray()) {
                return context.eval("js", "(" + config + ")");
            } else if (jsonNode.isObject()) {
                return context.eval("js", "(" + config + ")");
            } else {
                throw new IllegalArgumentException("Config must be a JSON object or array");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Config must be valid JSON: " + exception.getMessage(), exception);
        }
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
}
