package com.estateflow.model.enums;

/** Status filter for agreement lists, defined with the same date logic as Section 5.4. */
public enum AgreementFilter {
    ALL("All"), ACTIVE("Active"), UPCOMING("Upcoming"), EXPIRED("Expired"), TERMINATED("Terminated");

    private final String label;

    AgreementFilter(String label) {
        this.label = label;
    }

    public String getDisplayLabel() {
        return label;
    }
}
