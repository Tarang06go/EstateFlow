package com.estateflow.model.enums;

/** Derived: PENDING = nothing held yet, HELD = refundable balance remains, SETTLED = fully deducted, refunded or carried over. */
public enum DepositStatus {
    PENDING("Not collected"), HELD("Held"), SETTLED("Settled");

    private final String label;

    DepositStatus(String label) {
        this.label = label;
    }

    public String getDisplayLabel() {
        return label;
    }
}
