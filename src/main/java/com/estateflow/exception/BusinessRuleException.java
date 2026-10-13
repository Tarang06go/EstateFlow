package com.estateflow.exception;

/**
 * A business rule was violated. Carries the rule identifier (e.g. "BR-21").
 */
public class BusinessRuleException extends EstateFlowException {

    private final String ruleId;

    public BusinessRuleException(String message, String ruleId) {
        super(message);
        this.ruleId = ruleId;
    }

    public BusinessRuleException(String message, String ruleId, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String getRuleId() {
        return ruleId;
    }
}
