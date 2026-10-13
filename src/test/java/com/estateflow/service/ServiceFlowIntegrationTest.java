package com.estateflow.service;

import com.estateflow.dao.RentChargeDao;
import com.estateflow.dao.jdbc.*;
import com.estateflow.db.DatabaseManager;
import com.estateflow.db.JdbcTransactionManager;
import com.estateflow.dto.*;
import com.estateflow.exception.*;
import com.estateflow.model.*;
import com.estateflow.model.enums.*;
import com.estateflow.support.Fixtures;
import com.estateflow.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Key workflows against real MySQL with the real DAOs and transaction manager. */
class ServiceFlowIntegrationTest extends IntegrationTestBase {

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    @DisplayName("Create agreement -> partial pay -> overpay/advance rejected -> void -> receipt -> terminate -> vacancy")
    void fullRentalLifecycle() throws Exception {
        Owner owner = Fixtures.owner(ctx);
        Tenant tenant = Fixtures.tenant(ctx);
        Property property = Fixtures.property(ctx, owner);

        RentalAgreement a = ctx.agreements().create(property.getPropertyId(), tenant.getId(),
                LocalDate.of(2026, 8, 15), 3, bd("15000.00"), bd("30000.00"), "first lease");
        List<RentCharge> charges = ctx.payments().findCharges(a.getAgreementId());
        assertEquals(3, charges.size());
        assertEquals(LocalDate.of(2026, 11, 14), a.getEndDate());
        long aug = charges.get(0).getChargeId(), oct = charges.get(2).getChargeId();

        // BR-13 overlap, BR-20 advance payment
        BusinessRuleException overlap = assertThrows(BusinessRuleException.class, () -> ctx.agreements().create(
                property.getPropertyId(), tenant.getId(), LocalDate.of(2026, 9, 1), 6, bd("1.00"), null, null));
        assertEquals("BR-13", overlap.getRuleId());
        assertEquals("BR-20", assertThrows(BusinessRuleException.class,
                () -> ctx.payments().recordPayment(oct, bd("10.00"), TODAY, PaymentMethod.CASH, null, null)).getRuleId());

        // BR-21/23 partial payments and overpayment
        Payment wrong = ctx.payments().recordPayment(aug, bd("5000.00"), TODAY, PaymentMethod.CASH, null, null);
        assertEquals("BR-21", assertThrows(BusinessRuleException.class,
                () -> ctx.payments().recordPayment(aug, bd("10000.01"), TODAY, PaymentMethod.UPI, "R", null)).getRuleId());
        Payment good = ctx.payments().recordPayment(aug, bd("10000.00"), TODAY, PaymentMethod.UPI, "UPI-1", null);
        assertEquals("BR-21", assertThrows(BusinessRuleException.class,
                () -> ctx.payments().recordPayment(aug, bd("0.01"), TODAY, PaymentMethod.CASH, null, null)).getRuleId());
        assertEquals(ChargeStatus.PAID, ctx.payments().findCharges(a.getAgreementId()).get(0).status(TODAY));

        // BR-25 void: balance returns, second void and receipt are refused
        ctx.payments().voidPayment(wrong.getPaymentId(), "entered twice by mistake");
        assertEquals("BR-25", assertThrows(BusinessRuleException.class,
                () -> ctx.payments().voidPayment(wrong.getPaymentId(), "again")).getRuleId());
        assertEquals(bd("5000.00"), ctx.payments().findCharges(a.getAgreementId()).get(0).getBalance());
        assertEquals(bd("20000.00"), ctx.payments().outstandingBalance(a.getAgreementId()));
        assertEquals("BR-25", assertThrows(BusinessRuleException.class, () -> ctx.receipts().buildReceipt(wrong.getPaymentId())).getRuleId());
        PaymentReceiptData receipt = ctx.receipts().buildReceipt(good.getPaymentId());
        assertEquals(bd("5000.00"), receipt.currentBalance());
        assertEquals("RCP-2026-" + String.format("%06d", good.getPaymentId()), receipt.receiptNumber());

        // overdue + dashboard
        List<OverdueRentRow> overdue = ctx.payments().findOverdue();
        assertEquals(2, overdue.size());
        assertEquals(56, overdue.get(0).daysOverdue()); // Aug 15 -> Oct 10
        DashboardStats stats = ctx.dashboard().stats();
        assertEquals(1, stats.totalProperties());
        assertEquals(1, stats.occupiedProperties());
        assertEquals(new BigDecimal("100.00"), stats.occupancyPercent());
        assertEquals(bd("20000.00"), stats.outstandingBalance());
        assertEquals(2, stats.overdueCharges());
        assertEquals(bd("10000.00"), stats.rentCollectedThisMonth()); // voided 5000 excluded
        assertEquals(0, stats.leasesEndingSoon());           // lease ends Nov 14, window is 30 days from Oct 10
        assertEquals(0, ctx.agreements().findEndingSoon().size());
        assertEquals(1, stats.agreementsOccupyingToday());

        // BR-03/04 deactivation blocked while open
        assertEquals("BR-03", assertThrows(BusinessRuleException.class, () -> ctx.tenants().deactivate(tenant.getId())).getRuleId());
        assertEquals("BR-04", assertThrows(BusinessRuleException.class, () -> ctx.properties().deactivate(property.getPropertyId())).getRuleId());

        // BR-15 terminate: October charge cancelled, September (contains termination date) stays
        ctx.agreements().terminate(a.getAgreementId(), LocalDate.of(2026, 9, 30));
        List<RentCharge> after = ctx.payments().findCharges(a.getAgreementId());
        assertEquals(ChargeStatus.CANCELLED, after.get(2).status(TODAY));
        assertNull(after.get(1).getCancelledAt());
        assertEquals("BR-20", assertThrows(BusinessRuleException.class,
                () -> ctx.payments().recordPayment(oct, bd("1.00"), TODAY, PaymentMethod.CASH, null, null)).getRuleId());

        // BR-17 vacant after termination; back-to-back agreement allowed; deactivation now possible
        assertFalse(ctx.properties().findRows("").get(0).occupied());
        assertNotNull(ctx.agreements().create(property.getPropertyId(), tenant.getId(), LocalDate.of(2026, 10, 1), 1,
                bd("15000.00"), null, null));
        assertEquals(3, ctx.dashboard().stats().overdueCharges()); // Aug + Sep (terminated lease) + Oct 1 (new lease)
    }

    @Test
    @DisplayName("BR-15: valid payments after the termination date block termination until voided; all later charges cancel")
    void terminate_blockedByValidPayments_thenCancelsLaterCharges() throws Exception {
        Property property = Fixtures.property(ctx, Fixtures.owner(ctx));
        Tenant tenant = Fixtures.tenant(ctx);
        RentalAgreement a = ctx.agreements().create(property.getPropertyId(), tenant.getId(),
                LocalDate.of(2026, 1, 1), 12, bd("1000.00"), null, null);
        List<RentCharge> charges = ctx.payments().findCharges(a.getAgreementId());
        Payment octPayment = ctx.payments().recordPayment(charges.get(9).getChargeId(), bd("1000.00"), TODAY,
                PaymentMethod.BANK_TRANSFER, null, null);

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> ctx.agreements().terminate(a.getAgreementId(), LocalDate.of(2026, 3, 31)));
        assertEquals("BR-15", e.getRuleId());
        assertEquals(0, ctx.payments().findCharges(a.getAgreementId()).stream().filter(c -> c.getCancelledAt() != null).count());

        ctx.payments().voidPayment(octPayment.getPaymentId(), "paid on wrong agreement");
        ctx.agreements().terminate(a.getAgreementId(), LocalDate.of(2026, 3, 31));
        assertEquals(9, ctx.payments().findCharges(a.getAgreementId()).stream().filter(c -> c.getCancelledAt() != null).count());
        assertEquals(AgreementStatus.TERMINATED, ctx.agreements().get(a.getAgreementId()).getStatus());
        assertEquals("BR-15", assertThrows(BusinessRuleException.class,
                () -> ctx.agreements().terminate(a.getAgreementId(), LocalDate.of(2026, 2, 1))).getRuleId());
    }

    /** Decorator that fails after the agreement row was inserted, proving rollback leaves no partial data. */
    static class FailingChargeDao implements RentChargeDao {
        private final RentChargeDao delegate;

        FailingChargeDao(RentChargeDao delegate) {
            this.delegate = delegate;
        }

        public void insertAll(List<RentCharge> charges) {
            delegate.insertAll(charges.subList(0, 1)); // one charge written, then the failure
            throw new DataAccessException("simulated failure mid-creation", new java.sql.SQLException("boom", "HY000"));
        }

        public List<RentCharge> findByAgreement(Long id) { return delegate.findByAgreement(id); }
        public Optional<RentCharge> findById(Long id) { return delegate.findById(id); }
        public Optional<RentCharge> findByIdForUpdate(Long id) { return delegate.findByIdForUpdate(id); }
        public int cancelAfter(Long id, LocalDate d, LocalDateTime at) { return delegate.cancelAfter(id, d, at); }
    }

    @Test
    @DisplayName("Failure mid-creation leaves no agreement and no partial charges")
    void createAgreement_failureMidCreation_rollsBackEverything() throws Exception {
        Property property = Fixtures.property(ctx, Fixtures.owner(ctx));
        Tenant tenant = Fixtures.tenant(ctx);
        DatabaseManager db = ctx.databaseManager();
        AgreementService failing = new AgreementService(new JdbcAgreementDao(db),
                new FailingChargeDao(new JdbcRentChargeDao(db)), new JdbcPaymentDao(db), new JdbcPropertyDao(db),
                new JdbcTenantDao(db), new JdbcTransactionManager(db), ctx.audit(), CLOCK, 30);

        DataAccessException e = assertThrows(DataAccessException.class, () -> failing.create(property.getPropertyId(),
                tenant.getId(), LocalDate.of(2026, 1, 1), 6, bd("1000.00"), null, null));
        assertTrue(e.getMessage().contains("simulated"));
        assertEquals(0, ctx.agreements().findAllRows().size());
        assertTrue(ctx.properties().findRows("").get(0).propertyName() != null);
        try (var h = db.acquire(); var st = h.connection().createStatement();
             var rs = st.executeQuery("SELECT COUNT(*) FROM rent_charge")) {
            rs.next();
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    @DisplayName("Maintenance lifecycle and BR-30 tenant-history check against real data")
    void maintenanceLifecycle() throws Exception {
        Property property = Fixtures.property(ctx, Fixtures.owner(ctx));
        Tenant stranger = Fixtures.tenant(ctx);
        MaintenanceRequest r = new MaintenanceRequest();
        r.setPropertyId(property.getPropertyId());
        r.setTenantId(stranger.getId());
        r.setTitle("Leaking tap");
        r.setReportedDate(LocalDate.of(2026, 10, 5));
        assertEquals("BR-30", assertThrows(BusinessRuleException.class, () -> ctx.maintenance().open(r)).getRuleId());
        r.setTenantId(null);
        MaintenanceRequest opened = ctx.maintenance().open(r);
        ctx.maintenance().changeStatus(opened.getRequestId(), MaintenanceStatus.IN_PROGRESS, null);
        opened.setCost(bd("300.00"));
        ctx.maintenance().update(opened);
        ctx.maintenance().changeStatus(opened.getRequestId(), MaintenanceStatus.RESOLVED, null);
        MaintenanceReportRow row = ctx.maintenance().findRows(MaintenanceStatus.RESOLVED).get(0);
        assertEquals(TODAY, row.resolvedDate());
        assertEquals(bd("300.00"), row.cost());
        assertEquals(0, ctx.dashboard().stats().openMaintenanceByPriority().get(MaintenancePriority.MEDIUM));
    }

    @Test
    @DisplayName("BR-01/05: duplicate phones and inactive owners are rejected against real constraints")
    void partyRules_realDatabase() throws Exception {
        Owner o = Fixtures.owner(ctx);
        assertThrows(DuplicateEntityException.class,
                () -> ctx.owners().create(new Owner("Clone", o.getPhone(), null, null)));
        Property p = Fixtures.property(ctx, o);
        assertEquals("BR-02", assertThrows(BusinessRuleException.class, () -> ctx.owners().deactivate(o.getId())).getRuleId());
        ctx.properties().deactivate(p.getPropertyId());
        ctx.owners().deactivate(o.getId());
        assertEquals("BR-05", assertThrows(BusinessRuleException.class,
                () -> ctx.properties().create(newProperty(o))).getRuleId());
        assertEquals("BR-04", assertThrows(BusinessRuleException.class, () -> ctx.properties().reactivate(p.getPropertyId())).getRuleId());
        ctx.owners().reactivate(o.getId());
        ctx.properties().reactivate(p.getPropertyId());
    }

    private Property newProperty(Owner o) {
        Property p = new Property();
        p.setOwnerId(o.getId());
        p.setName("X");
        p.setPropertyType(PropertyType.HOUSE);
        p.setAddressLine("a");
        p.setCity("c");
        p.setListedRent(BigDecimal.ONE);
        return p;
    }

    @Test
    @DisplayName("Exports: CSV and receipt files are written from real data")
    void exportsFromRealData(@TempDir Path dir) throws Exception {
        Property property = Fixtures.property(ctx, Fixtures.owner(ctx));
        Tenant tenant = Fixtures.tenant(ctx);
        RentalAgreement a = ctx.agreements().create(property.getPropertyId(), tenant.getId(),
                LocalDate.of(2026, 9, 1), 2, bd("1000.00"), null, null);
        long charge = ctx.payments().findCharges(a.getAgreementId()).get(0).getChargeId();
        Payment p = ctx.payments().recordPayment(charge, bd("400.00"), TODAY, PaymentMethod.CARD, null, null);

        assertEquals(1, ctx.reports().exportPayments(TODAY.minusDays(30), TODAY, dir.resolve("p.csv")));
        assertEquals(2, ctx.reports().exportOverdue(dir.resolve("o.csv"))); // Sep 1 (400 paid) and Oct 1 charges
        assertEquals(1, ctx.reports().exportOccupancy(dir.resolve("occ.csv")));
        assertEquals(0, ctx.reports().exportMaintenance(null, dir.resolve("m.csv")));
        assertTrue(Files.readString(dir.resolve("p.csv")).contains("VALID"));
        Path receipt = dir.resolve("r.txt");
        ctx.receipts().writeReceipt(p.getPaymentId(), receipt);
        assertTrue(Files.readString(receipt).contains("RCP-2026-"));
    }
}
