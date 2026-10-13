package com.estateflow.dao;

import com.estateflow.dao.jdbc.*;
import com.estateflow.db.ConnectionHandle;
import com.estateflow.db.DatabaseManager;
import com.estateflow.exception.DuplicateKeyException;
import com.estateflow.model.*;
import com.estateflow.model.enums.*;
import com.estateflow.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JdbcDaoIntegrationTest extends IntegrationTestBase {

    DatabaseManager db;
    JdbcOwnerDao owners;
    JdbcTenantDao tenants;
    JdbcPropertyDao properties;
    JdbcAgreementDao agreements;
    JdbcRentChargeDao charges;
    JdbcPaymentDao payments;
    JdbcMaintenanceDao maintenance;
    long ownerId, tenantId, propertyId;

    @BeforeEach
    void daos() {
        db = ctx.databaseManager();
        owners = new JdbcOwnerDao(db);
        tenants = new JdbcTenantDao(db);
        properties = new JdbcPropertyDao(db);
        agreements = new JdbcAgreementDao(db);
        charges = new JdbcRentChargeDao(db);
        payments = new JdbcPaymentDao(db);
        maintenance = new JdbcMaintenanceDao(db);
        ownerId = owners.insert(new Owner("Asha Verma", "9000000010", "a@x.com", "Amritsar")).getId();
        tenantId = tenants.insert(new Tenant("Ravi Kumar", "9000000011", null, null)).getId();
        Property p = new Property();
        p.setOwnerId(ownerId);
        p.setName("Flat 4B");
        p.setPropertyType(PropertyType.APARTMENT);
        p.setAddressLine("12 Mall Road");
        p.setCity("Amritsar");
        p.setBedrooms(2);
        p.setListedRent(new BigDecimal("15000.00"));
        propertyId = properties.insert(p).getPropertyId();
    }

    private RentalAgreement agreement(LocalDate start, int months) {
        RentalAgreement a = new RentalAgreement();
        a.setPropertyId(propertyId);
        a.setTenantId(tenantId);
        a.setStartDate(start);
        a.setDurationMonths(months);
        a.setEndDate(start.plusMonths(months).minusDays(1));
        a.setMonthlyRent(new BigDecimal("15000.00"));
        a.setSecurityDeposit(new BigDecimal("30000.00"));
        return agreements.insert(a);
    }

    @Test
    @DisplayName("Owner CRUD round trip maps every column")
    void owner_roundTrip() {
        Owner o = owners.findById(ownerId).orElseThrow();
        assertEquals("Asha Verma", o.getFullName());
        assertTrue(o.isActive());
        assertNotNull(o.getCreatedAt());
        assertEquals(1, owners.search("asha").size());
        assertEquals(ownerId, owners.findByPhone("9000000010").orElseThrow().getId());
    }

    @Test
    @DisplayName("Section 9.1: update returns matched rows, also when nothing changed; zero for a missing row")
    void update_returnsMatchedRowCount() {
        Owner o = owners.findById(ownerId).orElseThrow();
        assertEquals(1, owners.update(o)); // identical values: matched, not "affected"
        o.setFullName("Changed");
        assertEquals(1, owners.update(o));
        Owner ghost = new Owner("Ghost", "9000000099", null, null);
        ghost.setId(987654L);
        assertEquals(0, owners.update(ghost));
    }

    @Test
    @DisplayName("Section 7.2: duplicate key becomes DuplicateKeyException keeping the SQLException cause")
    void duplicateKey_translated() {
        DuplicateKeyException e = assertThrows(DuplicateKeyException.class,
                () -> owners.insert(new Owner("Other", "9000000010", null, null)));
        assertInstanceOf(SQLException.class, e.getCause());
        assertEquals("uq_owner_phone", e.getConstraintName());
        assertTrue(e.getMessage().contains("insert owner"));
    }

    @Test
    @DisplayName("Tenant ID proof uniqueness and lookup")
    void tenant_idProof() {
        Tenant t = tenants.findById(tenantId).orElseThrow();
        t.setIdProofType("AADHAAR");
        t.setIdProofNumber("1111");
        assertEquals(1, tenants.update(t));
        assertEquals(tenantId, tenants.findByIdProof("AADHAAR", "1111").orElseThrow().getId());
        Tenant other = new Tenant("Other", "9000000012", null, null);
        other.setIdProofType("AADHAAR");
        other.setIdProofNumber("1111");
        DuplicateKeyException e = assertThrows(DuplicateKeyException.class, () -> tenants.insert(other));
        assertEquals("uq_tenant_id_proof", e.getConstraintName());
    }

    @Test
    void property_roundTripAndOwnerCount() {
        Property p = properties.findById(propertyId).orElseThrow();
        assertEquals(2, p.getBedrooms());
        assertEquals(new BigDecimal("15000.00"), p.getListedRent());
        assertEquals(1, properties.countActiveByOwner(ownerId));
        p.setActive(false);
        assertEquals(1, properties.update(p));
        assertEquals(0, properties.countActiveByOwner(ownerId));
    }

    @Test
    @DisplayName("BR-12: Java effective end equals the generated SQL column for active, terminated and expired rows")
    void effectiveEnd_javaAndSqlAgree() throws Exception {
        RentalAgreement active = agreement(LocalDate.of(2026, 1, 1), 12);
        RentalAgreement terminated = agreement(LocalDate.of(2027, 1, 1), 12);
        terminated.setStatus(AgreementStatus.TERMINATED);
        terminated.setTerminationDate(LocalDate.of(2027, 6, 30));
        assertEquals(1, agreements.update(terminated));
        RentalAgreement expired = agreement(LocalDate.of(2020, 1, 1), 6);
        for (RentalAgreement a : List.of(active, terminated, expired)) {
            RentalAgreement loaded = agreements.findById(a.getAgreementId()).orElseThrow();
            try (ConnectionHandle h = db.acquire(); Statement st = h.connection().createStatement();
                 ResultSet rs = st.executeQuery("SELECT effective_end_date FROM rental_agreement WHERE agreement_id = "
                         + a.getAgreementId())) {
                assertTrue(rs.next());
                assertEquals(loaded.getEffectiveEndDate(), rs.getObject(1, LocalDate.class));
            }
        }
    }

    @Test
    @DisplayName("BR-13/17: overlap, occupancy and vacancy queries share the effective period; back-to-back is allowed")
    void overlapAndOccupancy_useEffectivePeriod() {
        RentalAgreement a = agreement(LocalDate.of(2026, 1, 1), 12); // to 2026-12-31
        a.setStatus(AgreementStatus.TERMINATED);
        a.setTerminationDate(LocalDate.of(2026, 10, 31));
        agreements.update(a);
        assertEquals(1, agreements.findOverlapping(propertyId, LocalDate.of(2026, 10, 31), LocalDate.of(2027, 5, 1)).size());
        assertEquals(0, agreements.findOverlapping(propertyId, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 5, 1)).size());
        assertTrue(properties.findVacantOn(LocalDate.of(2026, 10, 10)).isEmpty());   // occupied until Oct 31
        assertEquals(1, properties.findVacantOn(LocalDate.of(2026, 11, 1)).size());  // freed the day after termination
        assertTrue(agreements.existsOpenForTenant(tenantId, TODAY));
        assertFalse(agreements.existsOpenForTenant(tenantId, LocalDate.of(2026, 11, 1)));
        assertTrue(agreements.existsOpenForProperty(propertyId, TODAY));
        assertTrue(agreements.existsForPropertyAndTenant(propertyId, tenantId));
        assertEquals(1, agreements.findEndingBetween(TODAY, TODAY.plusDays(30)).size());
        assertEquals(0, agreements.findEndingBetween(TODAY, TODAY.plusDays(10)).size());
    }

    @Test
    @DisplayName("Section 8: a terminated agreement must satisfy the termination check constraint")
    void agreement_terminationConstraint_enforcedByDatabase() {
        RentalAgreement a = agreement(LocalDate.of(2026, 1, 1), 12);
        a.setStatus(AgreementStatus.TERMINATED);
        a.setTerminationDate(LocalDate.of(2026, 12, 31)); // not < end_date
        assertThrows(com.estateflow.exception.DataAccessException.class, () -> agreements.update(a));
    }

    @Test
    @DisplayName("Charges: batch insert, amountPaid counts valid payments only, cancellation and UTC timestamp round trip")
    void charges_paymentsAndCancellation() {
        RentalAgreement a = agreement(LocalDate.of(2026, 8, 1), 3);
        charges.insertAll(com.estateflow.service.rules.RentScheduleGenerator.generate(a.getAgreementId(),
                a.getStartDate(), 3, a.getMonthlyRent()));
        List<RentCharge> list = charges.findByAgreement(a.getAgreementId());
        assertEquals(3, list.size());
        long chargeId = list.get(0).getChargeId();
        Payment p1 = payments.insert(new Payment(chargeId, new BigDecimal("5000.00"), LocalDate.of(2026, 8, 5), PaymentMethod.CASH, null, null));
        Payment p2 = payments.insert(new Payment(chargeId, new BigDecimal("2000.00"), LocalDate.of(2026, 8, 6), PaymentMethod.UPI, "R1", "n"));
        assertEquals(new BigDecimal("7000.00"), charges.findById(chargeId).orElseThrow().getAmountPaid());

        LocalDateTime at = LocalDateTime.of(2026, 10, 10, 8, 30, 15);
        assertEquals(1, payments.markVoided(p2.getPaymentId(), "typo", at));
        assertEquals(0, payments.markVoided(p2.getPaymentId(), "again", at)); // conditional: already voided
        Payment voided = payments.findById(p2.getPaymentId()).orElseThrow();
        assertEquals(at, voided.getVoidedAt()); // UTC round trip, no zone shift
        assertEquals("typo", voided.getVoidReason());
        assertEquals(new BigDecimal("5000.00"), charges.findById(chargeId).orElseThrow().getAmountPaid());
        assertEquals(2, payments.findByCharge(chargeId).size());
        assertEquals(2, payments.findByDateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)).size());

        assertTrue(payments.existsValidForChargesAfter(a.getAgreementId(), LocalDate.of(2026, 7, 31)));
        assertFalse(payments.existsValidForChargesAfter(a.getAgreementId(), LocalDate.of(2026, 8, 1)));

        LocalDateTime cancelledAt = LocalDateTime.of(2026, 10, 10, 9, 0, 1);
        assertEquals(2, charges.cancelAfter(a.getAgreementId(), LocalDate.of(2026, 8, 1), cancelledAt));
        assertEquals(0, charges.cancelAfter(a.getAgreementId(), LocalDate.of(2026, 8, 1), cancelledAt));
        List<RentCharge> after = charges.findByAgreement(a.getAgreementId());
        assertNull(after.get(0).getCancelledAt());
        assertEquals(cancelledAt, after.get(1).getCancelledAt());
        assertNotNull(p1.getPaymentId());
    }

    @Test
    void maintenance_roundTripAndCounts() {
        MaintenanceRequest r = new MaintenanceRequest();
        r.setPropertyId(propertyId);
        r.setTitle("Leak");
        r.setPriority(MaintenancePriority.HIGH);
        r.setStatus(MaintenanceStatus.OPEN);
        r.setReportedDate(LocalDate.of(2026, 10, 1));
        maintenance.insert(r);
        MaintenanceRequest loaded = maintenance.findById(r.getRequestId()).orElseThrow();
        assertNull(loaded.getTenantId());
        loaded.setStatus(MaintenanceStatus.RESOLVED);
        loaded.setResolvedDate(LocalDate.of(2026, 10, 5));
        loaded.setCost(new BigDecimal("250.00"));
        assertEquals(1, maintenance.update(loaded));
        assertEquals(1, maintenance.findByStatus(MaintenanceStatus.RESOLVED).size());
        Map<MaintenancePriority, Integer> counts = maintenance.countOpenByPriority();
        assertEquals(0, counts.get(MaintenancePriority.HIGH));
        r.setRequestId(null);
        r.setStatus(MaintenanceStatus.RESOLVED);
        r.setResolvedDate(LocalDate.of(2026, 9, 1)); // before reported_date: schema check ck_maint_resolution
        assertThrows(com.estateflow.exception.DataAccessException.class, () -> maintenance.insert(r));
    }
}
