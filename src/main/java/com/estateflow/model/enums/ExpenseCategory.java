package com.estateflow.model.enums;

/** Operating expense categories. Financing (loan/mortgage) and capital items are deliberately not offered. */
public enum ExpenseCategory {
    MAINTENANCE("Maintenance"),
    UTILITIES("Utilities"),
    INSURANCE("Insurance"),
    PROPERTY_TAX("Property tax"),
    REPAIRS("Repairs"),
    MANAGEMENT_FEES("Management fees"),
    OTHER("Other");

    private final String displayLabel;

    ExpenseCategory(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    @Override
    public String toString() {
        return displayLabel;
    }
}
