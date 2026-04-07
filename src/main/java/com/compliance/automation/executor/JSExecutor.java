package com.compliance.automation.executor;

import java.io.IOException;

import com.compliance.automation.model.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.springframework.stereotype.Service;

@Service
public class JSExecutor {

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
        try (Context context = Context.newBuilder("js")
                .allowAllAccess(false)
                .build()) {
            context.eval("js", jsCode);

            Value checkFunction = context.getBindings("js").getMember(CHECK_FUNCTION_NAME);
            if (checkFunction == null || !checkFunction.canExecute()) {
                return new Result(ruleId, STATUS_MISSING_FUNCTION, "Missing executable check(config) function", -1);
            }

            Value configValue = parseAndConvertConfig(context, config);
            Value executionResult = checkFunction.execute(configValue);

            return new Result(
                    ruleId,
                    extractString(executionResult, "status"),
                    extractString(executionResult, "evidence"),
                    extractInt(executionResult, "lineNumber"));
        } catch (PolyglotException exception) {
            if (exception.isSyntaxError()) {
                return new Result(ruleId, STATUS_INVALID_JS, exception.getMessage(), -1);
            }
            return new Result(ruleId, STATUS_RUNTIME_ERROR, exception.getMessage(), -1);
        } catch (IllegalArgumentException exception) {
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
}
