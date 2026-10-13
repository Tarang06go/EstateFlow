package com.estateflow.model.enums;

public enum MaintenancePriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT;

    /**
     * Returns a natural ordering value where URGENT is highest.
     */
    public int getOrder() {
        return ordinal();
    }
}
