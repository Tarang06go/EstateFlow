package com.estateflow.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnumsTest {

    @Test
    @DisplayName("MaintenanceStatus canTransitionTo encodes the correct workflow")
    void testMaintenanceStatusWorkflow() {
        // OPEN -> IN_PROGRESS or CANCELLED
        assertTrue(MaintenanceStatus.OPEN.canTransitionTo(MaintenanceStatus.IN_PROGRESS));
        assertTrue(MaintenanceStatus.OPEN.canTransitionTo(MaintenanceStatus.CANCELLED));
        assertFalse(MaintenanceStatus.OPEN.canTransitionTo(MaintenanceStatus.RESOLVED));
        assertFalse(MaintenanceStatus.OPEN.canTransitionTo(MaintenanceStatus.OPEN));

        // IN_PROGRESS -> RESOLVED or CANCELLED
        assertTrue(MaintenanceStatus.IN_PROGRESS.canTransitionTo(MaintenanceStatus.RESOLVED));
        assertTrue(MaintenanceStatus.IN_PROGRESS.canTransitionTo(MaintenanceStatus.CANCELLED));
        assertFalse(MaintenanceStatus.IN_PROGRESS.canTransitionTo(MaintenanceStatus.OPEN));

        // RESOLVED is terminal
        assertFalse(MaintenanceStatus.RESOLVED.canTransitionTo(MaintenanceStatus.OPEN));
        assertFalse(MaintenanceStatus.RESOLVED.canTransitionTo(MaintenanceStatus.IN_PROGRESS));
        assertFalse(MaintenanceStatus.RESOLVED.canTransitionTo(MaintenanceStatus.CANCELLED));

        // CANCELLED is terminal
        assertFalse(MaintenanceStatus.CANCELLED.canTransitionTo(MaintenanceStatus.OPEN));
        assertFalse(MaintenanceStatus.CANCELLED.canTransitionTo(MaintenanceStatus.IN_PROGRESS));
        assertFalse(MaintenanceStatus.CANCELLED.canTransitionTo(MaintenanceStatus.RESOLVED));

        // null target
        assertFalse(MaintenanceStatus.OPEN.canTransitionTo(null));
    }

    @Test
    @DisplayName("PropertyType and PaymentMethod display labels")
    void testDisplayLabels() {
        assertEquals("Apartment", PropertyType.APARTMENT.getDisplayLabel());
        assertEquals("Villa", PropertyType.VILLA.toString());

        assertEquals("Cash", PaymentMethod.CASH.getDisplayLabel());
        assertEquals("UPI", PaymentMethod.UPI.toString());
        assertEquals("Bank Transfer", PaymentMethod.BANK_TRANSFER.getDisplayLabel());
    }

    @Test
    @DisplayName("MaintenancePriority ordering")
    void testPriorityOrdering() {
        assertTrue(MaintenancePriority.URGENT.getOrder() > MaintenancePriority.HIGH.getOrder());
        assertTrue(MaintenancePriority.HIGH.getOrder() > MaintenancePriority.MEDIUM.getOrder());
        assertTrue(MaintenancePriority.MEDIUM.getOrder() > MaintenancePriority.LOW.getOrder());
    }
}
