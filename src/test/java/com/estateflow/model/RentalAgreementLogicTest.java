package com.estateflow.model;

import com.estateflow.model.enums.AgreementStatus;
import com.estateflow.model.enums.EffectiveAgreementStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class RentalAgreementLogicTest {

    private RentalAgreement agreement;

    @BeforeEach
    void setUp() {
        agreement = new RentalAgreement();
        agreement.setAgreementId(1L);
        agreement.setPropertyId(10L);
        agreement.setTenantId(20L);
        agreement.setStartDate(LocalDate.of(2026, 1, 1));
        agreement.setDurationMonths(12);
        agreement.setEndDate(LocalDate.of(2026, 12, 31));
        agreement.setMonthlyRent(new BigDecimal("25000.00"));
        agreement.setStatus(AgreementStatus.ACTIVE);
    }

    @Test
    @DisplayName("BR-12: Effective end date is endDate when active, terminationDate when terminated")
    void testEffectiveEndDate() {
        assertEquals(LocalDate.of(2026, 12, 31), agreement.getEffectiveEndDate());

        agreement.setStatus(AgreementStatus.TERMINATED);
        agreement.setTerminationDate(LocalDate.of(2026, 6, 30));
        assertEquals(LocalDate.of(2026, 6, 30), agreement.getEffectiveEndDate());
    }

    @Test
    @DisplayName("BR-17: occupies(date) correctly determines occupancy")
    void testOccupies() {
        assertTrue(agreement.occupies(LocalDate.of(2026, 1, 1)));
        assertTrue(agreement.occupies(LocalDate.of(2026, 6, 15)));
        assertTrue(agreement.occupies(LocalDate.of(2026, 12, 31)));

        assertFalse(agreement.occupies(LocalDate.of(2025, 12, 31)));
        assertFalse(agreement.occupies(LocalDate.of(2027, 1, 1)));

        // If terminated early
        agreement.setStatus(AgreementStatus.TERMINATED);
        agreement.setTerminationDate(LocalDate.of(2026, 6, 30));
        assertTrue(agreement.occupies(LocalDate.of(2026, 6, 30)));
        assertFalse(agreement.occupies(LocalDate.of(2026, 7, 1)));
    }

    @Test
    @DisplayName("BR-13: overlaps(from, to) tests date intersections")
    void testOverlaps() {
        // Enclosed period
        assertTrue(agreement.overlaps(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 1)));
        // Overlapping at start
        assertTrue(agreement.overlaps(LocalDate.of(2025, 11, 1), LocalDate.of(2026, 2, 1)));
        // Overlapping at end
        assertTrue(agreement.overlaps(LocalDate.of(2026, 11, 1), LocalDate.of(2027, 2, 1)));
        // Encompassing
        assertTrue(agreement.overlaps(LocalDate.of(2025, 1, 1), LocalDate.of(2027, 1, 1)));

        // Strictly before
        assertFalse(agreement.overlaps(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));
        // Strictly after
        assertFalse(agreement.overlaps(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31)));
    }

    @Test
    @DisplayName("BR-26 / BR-27: effectiveStatus derives status correctly")
    void testEffectiveStatus() {
        int warningDays = 30;

        // Before start date -> UPCOMING
        assertEquals(EffectiveAgreementStatus.UPCOMING,
                agreement.effectiveStatus(LocalDate.of(2025, 12, 1), warningDays));

        // Normal active -> ACTIVE
        assertEquals(EffectiveAgreementStatus.ACTIVE,
                agreement.effectiveStatus(LocalDate.of(2026, 6, 1), warningDays));

        // Within 30 days of end date -> EXPIRING_SOON
        assertEquals(EffectiveAgreementStatus.EXPIRING_SOON,
                agreement.effectiveStatus(LocalDate.of(2026, 12, 15), warningDays));
        assertEquals(EffectiveAgreementStatus.EXPIRING_SOON,
                agreement.effectiveStatus(LocalDate.of(2026, 12, 31), warningDays));

        // After end date -> EXPIRED
        assertEquals(EffectiveAgreementStatus.EXPIRED,
                agreement.effectiveStatus(LocalDate.of(2027, 1, 1), warningDays));

        // Terminated agreement
        agreement.setStatus(AgreementStatus.TERMINATED);
        agreement.setTerminationDate(LocalDate.of(2026, 6, 30));
        assertEquals(EffectiveAgreementStatus.TERMINATED,
                agreement.effectiveStatus(LocalDate.of(2026, 7, 1), warningDays));
    }

    @Test
    @DisplayName("Section 5.4: terminated agreement inside its notice period is EXPIRING_SOON/ACTIVE, not TERMINATED")
    void effectiveStatus_terminatedWithinNoticePeriod_usesDatesOnly() {
        RentalAgreement a = new RentalAgreement();
        a.setStartDate(LocalDate.of(2026, 1, 1));
        a.setDurationMonths(12);
        a.setEndDate(LocalDate.of(2026, 12, 31));
        a.setStatus(AgreementStatus.TERMINATED);
        a.setTerminationDate(LocalDate.of(2026, 6, 30));
        assertEquals(EffectiveAgreementStatus.EXPIRING_SOON, a.effectiveStatus(LocalDate.of(2026, 6, 15), 30));
        assertEquals(EffectiveAgreementStatus.ACTIVE, a.effectiveStatus(LocalDate.of(2026, 3, 1), 30));
        assertEquals(EffectiveAgreementStatus.TERMINATED, a.effectiveStatus(LocalDate.of(2026, 7, 1), 30));
    }
}
