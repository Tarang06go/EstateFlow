package com.estateflow.model.enums;

/**
 * Status of a maintenance request with workflow transition rules.
 * Workflow: OPEN -> IN_PROGRESS -> RESOLVED; OPEN or IN_PROGRESS -> CANCELLED.
 * RESOLVED and CANCELLED are terminal.
 */
public enum MaintenanceStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CANCELLED;

    /**
     * Returns whether a transition from this status to the target is allowed.
     */
    public boolean canTransitionTo(MaintenanceStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case OPEN -> target == IN_PROGRESS || target == CANCELLED;
            case IN_PROGRESS -> target == RESOLVED || target == CANCELLED;
            case RESOLVED, CANCELLED -> false; // terminal states
        };
    }
}
