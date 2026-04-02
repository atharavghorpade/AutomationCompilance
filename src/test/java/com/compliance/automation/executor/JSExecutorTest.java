package com.compliance.automation.executor;

import com.compliance.automation.model.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JSExecutorTest {

    private JSExecutor jsExecutor;

    @BeforeEach
    void setUp() {
        jsExecutor = new JSExecutor(new ObjectMapper());
    }

    @Test
    void testExecuteWithPassingCondition() {
        String js = """
function check(config) {
    if (config.includes("aaa new-model")) {
        return { status: "PASS", evidence: "found", lineNumber: 1 };
    }
    return { status: "FAIL", evidence: "missing", lineNumber: -1 };
}
""";

        String config = "[\"aaa new-model\", \"other-value\"]";
        String ruleId = "RULE-001";

        Result result = jsExecutor.execute(js, config, ruleId);

        assertNotNull(result);
        assertEquals(ruleId, result.getRuleId());
        assertEquals("PASS", result.getStatus());
        assertEquals("found", result.getEvidence());
        assertEquals(1, result.getLineNumber());
    }

    @Test
    void testExecuteWithFailingCondition() {
        String js = """
function check(config) {
    if (config.includes("aaa new-model")) {
        return { status: "PASS", evidence: "found", lineNumber: 1 };
    }
    return { status: "FAIL", evidence: "missing", lineNumber: -1 };
}
""";

        String config = "[\"other-value\"]";
        String ruleId = "RULE-002";

        Result result = jsExecutor.execute(js, config, ruleId);

        assertNotNull(result);
        assertEquals(ruleId, result.getRuleId());
        assertEquals("FAIL", result.getStatus());
        assertEquals("missing", result.getEvidence());
        assertEquals(-1, result.getLineNumber());
    }

    @Test
    void testExecuteWithInvalidJS() {
        String invalidJs = "function check(config) { this is invalid }";
        String config = "[]";
        String ruleId = "RULE-003";

        Result result = jsExecutor.execute(invalidJs, config, ruleId);

        assertNotNull(result);
        assertEquals(ruleId, result.getRuleId());
        assertEquals("INVALID_JS", result.getStatus());
        assertNotNull(result.getEvidence());
        assertEquals(-1, result.getLineNumber());
    }

    @Test
    void testExecuteWithMissingFunction() {
        String jsWithoutCheck = "var x = 5;";
        String config = "[]";
        String ruleId = "RULE-004";

        Result result = jsExecutor.execute(jsWithoutCheck, config, ruleId);

        assertNotNull(result);
        assertEquals(ruleId, result.getRuleId());
        assertEquals("MISSING_FUNCTION", result.getStatus());
        assertEquals("Missing executable check(config) function", result.getEvidence());
        assertEquals(-1, result.getLineNumber());
    }

    @Test
    void testExecuteWithInvalidConfig() {
        String js = """
function check(config) {
    return { status: "OK", evidence: "tested", lineNumber: 0 };
}
""";
        String invalidConfig = "{ not valid json }";
        String ruleId = "RULE-005";

        Result result = jsExecutor.execute(js, invalidConfig, ruleId);

        assertNotNull(result);
        assertEquals(ruleId, result.getRuleId());
        assertEquals("INVALID_CONFIG", result.getStatus());
        assertNotNull(result.getEvidence());
        assertEquals(-1, result.getLineNumber());
    }

    @Test
    void testExecuteWithRuntimeError() {
        String jsWithError = """
function check(config) {
    throw new Error("Runtime error occurred");
}
""";
        String config = "[]";
        String ruleId = "RULE-006";

        Result result = jsExecutor.execute(jsWithError, config, ruleId);

        assertNotNull(result);
        assertEquals(ruleId, result.getRuleId());
        assertEquals("RUNTIME_ERROR", result.getStatus());
        assertNotNull(result.getEvidence());
        assertEquals(-1, result.getLineNumber());
    }
}
