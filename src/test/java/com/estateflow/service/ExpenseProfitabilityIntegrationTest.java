package com.estateflow.service;

import com.estateflow.dto.AuditEntry;
import com.estateflow.dto.ExpenseRow;
import com.estateflow.dto.ProfitabilityReport;
import com.estateflow.dto.ProfitabilityReport.PropertyProfit;
import com.estateflow.exception.*;
import com.estateflow.dao.jdbc.*;
import com.estateflow.db.JdbcTransactionManager;
import com.estateflow.model.*;
import com.estateflow.model.enums.*;
import com.estateflow.support.Fixtures;
import com.estateflow.support.IntegrationTestBase;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real MySQL (estateflow_test only). Needs the v1.1-v1.4 migrations on the test schema; skips itself when v1.4 is missing.
 * The SQL here is what decides date-range filtering, voided payments, soft deletes and the maintenance double-count rule.
 */
class ExpenseProfitabilityIntegrationTest extends IntegrationTestBase {

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static final LocalDate JUL1 = LocalDate.of(2026, 7, 1), JUL31 = LocalDate.of(2026, 7, 31);

    private Property propertyA, propertyB;

    @BeforeEach
    void requireMigration() throws Exception {
        try {
            ctx.expenses().ensureReady();
        } catch (ConfigurationException e) {
            Assumptions.abort("apply db/migrations/v1.4_expense.sql to estateflow_test to run the Phase 3 integration tests");
        }
        Owner owner = Fixtures.owner(ctx);
        propertyA = Fixtures.property(ctx, owner);
        propertyB = Fixtures.property(ctx, owner);
    }

    private String scalar(String sql) throws Exception {
        try (var h = ctx.databaseManager().acquire(); Statement st = h.connection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private Expense expense(Property p, ExpenseCategory c, String amount, LocalDate date, String description) throws Exception {
        Expense e = new Expense();
        e.setPropertyId(p.getPropertyId());
        e.setCategory(c);
        e.setAmount(bd(amount));
        e.setExpenseDate(date);
        e.setDescription(description);
        e.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        return ctx.expenses().create(e);
    }

    /** Agreement from 2026-06-01 for 4 months at 10000: charges Jun, Jul, Aug, Sep. */
    private List<RentCharge> leaseOnA() throws Exception {
        Tenant tenant = Fixtures.tenant(ctx);
        RentalAgreement a = ctx.agreements().create(propertyA.getPropertyId(), tenant.getId(), LocalDate.of(2026, 6, 1), 4,
                bd("10000.00"), bd("20000.00"), "lease");
        return ctx.payments().findCharges(a.getAgreementId());
    }

    private MaintenanceRequest resolvedRequest(Property p, String cost, LocalDate resolved) throws Exception {
        MaintenanceRequest r = new MaintenanceRequest();
        r.setPropertyId(p.getPropertyId());
        r.setTitle("Fix " + cost);
        r.setPriority(MaintenancePriority.MEDIUM);
        r.setReportedDate(resolved.minusDays(3));
        MaintenanceRequest opened = ctx.maintenance().open(r);
        ctx.maintenance().changeStatus(opened.getRequestId(), MaintenanceStatus.IN_PROGRESS, null);
        MaintenanceRequest edit = ctx.maintenance().get(opened.getRequestId());
        edit.setCost(bd(cost));
        ctx.maintenance().update(edit);
        return ctx.maintenance().changeStatus(opened.getRequestId(), MaintenanceStatus.RESOLVED, resolved);
    }

    private ProfitabilityReport julyReport(Long propertyId) throws Exception {
        return ctx.profitability().report(JUL1, JUL31, propertyId);
    }

    @Test
    void advancedAnalyticsMatchesPaymentsToTheDueChargesAndAgesCurrentArrears() throws Exception {
        Tenant tenant = Fixtures.tenant(ctx);
        RentalAgreement agreement = ctx.agreements().create(propertyA.getPropertyId(), tenant.getId(),
                TODAY.withDayOfMonth(1), 1, bd("10000.00"), BigDecimal.ZERO, "analytics fixture");
        RentCharge charge = ctx.payments().findCharges(agreement.getAgreementId()).getFirst();
        ctx.payments().recordPayment(charge.getChargeId(), bd("4000.00"), TODAY,
                PaymentMethod.BANK_TRANSFER, "ANALYTICS-IT", null);

        var report = ctx.analytics().report(charge.getDueDate(), charge.getDueDate());
        assertEquals(bd("10000.00"), report.collection().billed());
        assertEquals(bd("4000.00"), report.collection().collectedToDate());
        assertEquals(bd("6000.00"), report.collection().outstanding());
        assertEquals(bd("40.00"), report.collection().ratePercent());
        assertEquals(1, report.arrears().getFirst().charges());
        assertEquals("1-30 days", report.arrears().getFirst().label());
        assertEquals(bd("6000.00"), report.arrears().getFirst().balance());
    }

    // ---------------------------------------------------------------- expenses

    @Test
    void expenseCrudSoftDeleteAndAuditAttribution() throws Exception {
        Expense created = expense(propertyA, ExpenseCategory.UTILITIES, "1250.50", LocalDate.of(2026, 7, 5), "Electricity");
        assertNotNull(created.getExpenseId());
        assertEquals(bd("1250.50"), ctx.expenses().get(created.getExpenseId()).getAmount());

        Expense edit = ctx.expenses().get(created.getExpenseId());
        edit.setAmount(bd("1300.00"));
        edit.setReferenceNo("INV-77");
        assertEquals(bd("1300.00"), ctx.expenses().update(edit).getAmount());

        ctx.expenses().delete(created.getExpenseId(), "entered by mistake");
        assertEquals("1", scalar("SELECT COUNT(*) FROM expense WHERE expense_id = " + created.getExpenseId() + " AND deleted_at IS NOT NULL"),
                "soft delete keeps the row");
        assertEquals("entered by mistake", scalar("SELECT delete_reason FROM expense WHERE expense_id = " + created.getExpenseId()));
        assertTrue(ctx.expenses().search(null, null, null, null, false).isEmpty(), "deleted rows are hidden by default");
        List<ExpenseRow> withDeleted = ctx.expenses().search(null, null, null, null, true);
        assertEquals(1, withDeleted.size());
        assertTrue(withDeleted.get(0).expense().isDeleted());
        assertEquals("EXP-04",
                assertThrows(BusinessRuleException.class, () -> ctx.expenses().update(withDeleted.get(0).expense())).getRuleId());

        List<AuditEntry> trail = ctx.audit().find("EXPENSE", created.getExpenseId(), 10);
        assertEquals(List.of("DELETED", "UPDATED", "CREATED"), trail.stream().map(AuditEntry::action).toList());
        assertTrue(trail.stream().allMatch(e -> "it-admin".equals(e.actorUsername()) && Long.valueOf(1).equals(e.actorUserId())));
        assertTrue(trail.get(1).oldValue().contains("1250.50") && trail.get(1).newValue().contains("1300.00"));
    }

    @Test
    void searchFiltersByPropertyCategoryAndInclusiveDateRange() throws Exception {
        expense(propertyA, ExpenseCategory.UTILITIES, "10.00", LocalDate.of(2026, 6, 30), "before");
        expense(propertyA, ExpenseCategory.UTILITIES, "20.00", JUL1, "first day");
        expense(propertyA, ExpenseCategory.INSURANCE, "30.00", JUL31, "last day");
        expense(propertyA, ExpenseCategory.UTILITIES, "40.00", LocalDate.of(2026, 8, 1), "after");
        expense(propertyB, ExpenseCategory.UTILITIES, "50.00", JUL1, "other property");
        assertEquals(2, ctx.expenses().search(propertyA.getPropertyId(), null, JUL1, JUL31, false).size());
        assertEquals(1, ctx.expenses().search(propertyA.getPropertyId(), ExpenseCategory.INSURANCE, JUL1, JUL31, false).size());
        assertEquals(5, ctx.expenses().search(null, null, null, null, false).size());
        assertEquals(1, ctx.expenses().search(propertyB.getPropertyId(), null, null, null, false).size());
    }

    @Test
    void identicalExpenseIsRefusedButADifferentDescriptionIsAllowed() throws Exception {
        expense(propertyA, ExpenseCategory.OTHER, "99.00", JUL1, "Gardener");
        assertEquals("EXP-01", assertThrows(BusinessRuleException.class,
                () -> expense(propertyA, ExpenseCategory.OTHER, "99.00", JUL1, "gardener")).getRuleId());
        assertDoesNotThrow(() -> expense(propertyA, ExpenseCategory.OTHER, "99.00", JUL1, "Gardener (second visit)"));
        assertEquals("2", scalar("SELECT COUNT(*) FROM expense"));
    }

    @Test
    void databaseChecksBackUpTheServiceValidation() throws Exception {
        try (var h = ctx.databaseManager().acquire(); Statement st = h.connection().createStatement()) {
            assertThrows(java.sql.SQLException.class, () -> st.execute("INSERT INTO expense (property_id, category, amount, expense_date, "
                    + "description, payment_method) VALUES (" + propertyA.getPropertyId() + ", 'OTHER', -5, '2026-07-01', 'x', 'CASH')"));
            assertThrows(java.sql.SQLException.class, () -> st.execute("INSERT INTO expense (property_id, category, amount, expense_date, "
                    + "description, payment_method) VALUES (" + propertyA.getPropertyId() + ", 'MORTGAGE', 5, '2026-07-01', 'x', 'CASH')"));
        }
    }

    // ---------------------------------------------------------------- authorization + failure

    @Test
    void viewerCannotWriteExpensesButCanRead() throws Exception {
        ctx.session().logout();
        ctx.auth().createInitialAdministrator("boss", "Integration-Pass-1");
        ctx.auth().login("boss", "Integration-Pass-1");
        ctx.users().createUser("reader.v", "Pages-Pass-12345", Role.VIEWER);
        ctx.users().createUser("mgr.exp", "Manager-Pass-12345", Role.MANAGER);

        ctx.auth().logout();
        ctx.auth().login("mgr.exp", "Manager-Pass-12345");
        Expense byManager = expense(propertyA, ExpenseCategory.OTHER, "75.00", JUL1, "Manager entry");
        AuditEntry attributed = ctx.audit().find("EXPENSE", byManager.getExpenseId(), 5).get(0);
        assertEquals("mgr.exp", attributed.actorUsername());
        assertNotNull(attributed.actorUserId());

        ctx.auth().logout();
        ctx.auth().login("reader.v", "Pages-Pass-12345");
        assertThrows(AuthorizationException.class, () -> expense(propertyA, ExpenseCategory.OTHER, "1.00", JUL1, "nope"));
        assertThrows(AuthorizationException.class, () -> ctx.expenses().delete(byManager.getExpenseId(), "nope"));
        Expense edit = ctx.expenses().get(byManager.getExpenseId());
        edit.setAmount(bd("2.00"));
        assertThrows(AuthorizationException.class, () -> ctx.expenses().update(edit));
        assertEquals(1, ctx.expenses().search(null, null, null, null, false).size(), "viewer can read");
        assertEquals(bd("75.00"), ctx.expenses().get(byManager.getExpenseId()).getAmount(), "nothing changed");
        assertDoesNotThrow(() -> ctx.profitability().report(JUL1, JUL31, null));

        ctx.auth().logout();
        assertThrows(AuthorizationException.class, () -> ctx.expenses().search(null, null, null, null, false));
        assertThrows(AuthorizationException.class, () -> ctx.profitability().report(JUL1, JUL31, null));
    }

    @Test
    void failedAuditWriteRollsBackTheExpense() throws Exception {
        var db = ctx.databaseManager();
        ExpenseService failing = new ExpenseService(new JdbcExpenseDao(db), new JdbcPropertyDao(db), new JdbcMaintenanceDao(db),
                new JdbcTransactionManager(db), new AuditService(new V11IntegrationTest.FailingAuditDao(), Clock.systemUTC(), ctx.authorizer()),
                CLOCK, ctx.authorizer());
        Expense e = new Expense();
        e.setPropertyId(propertyA.getPropertyId());
        e.setCategory(ExpenseCategory.OTHER);
        e.setAmount(bd("10.00"));
        e.setExpenseDate(JUL1);
        e.setDescription("should roll back");
        e.setPaymentMethod(PaymentMethod.CASH);
        assertThrows(DataAccessException.class, () -> failing.create(e));
        assertEquals("0", scalar("SELECT COUNT(*) FROM expense"));
    }

    // ---------------------------------------------------------------- profitability SQL

    @Test
    void rentReceivedUsesValidPaymentsInclusiveDateRangeAndShowsChargedSeparately() throws Exception {
        List<RentCharge> charges = leaseOnA();
        RentCharge jun = charges.get(0), jul = charges.get(1), aug = charges.get(2);
        ctx.payments().recordPayment(jun.getChargeId(), bd("3000.00"), LocalDate.of(2026, 6, 30), PaymentMethod.CASH, null, null); // day before
        ctx.payments().recordPayment(jul.getChargeId(), bd("4000.00"), JUL1, PaymentMethod.UPI, null, null);                       // first day
        ctx.payments().recordPayment(jul.getChargeId(), bd("2000.00"), JUL31, PaymentMethod.UPI, null, null);                      // last day
        Payment wrong = ctx.payments().recordPayment(jul.getChargeId(), bd("1000.00"), LocalDate.of(2026, 7, 15), PaymentMethod.CASH, null, null);
        ctx.payments().voidPayment(wrong.getPaymentId(), "entered twice");                                                          // voided
        ctx.payments().recordPayment(aug.getChargeId(), bd("10000.00"), LocalDate.of(2026, 8, 1), PaymentMethod.CASH, null, null);   // day after

        ProfitabilityReport r = julyReport(null);
        assertEquals(bd("6000.00"), r.rentReceived(), "boundary days in, day before/after out, voided out");
        BigDecimal expectedCharged = charges.stream().filter(c -> !c.getDueDate().isBefore(JUL1) && !c.getDueDate().isAfter(JUL31))
                .map(RentCharge::getAmountDue).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(expectedCharged.setScale(2), r.rentCharged());
        assertNotEquals(r.rentReceived(), r.rentCharged(), "received is not charged");
        assertEquals(bd("0.00"), r.expenses());
        assertEquals(bd("6000.00"), r.noi());
        assertEquals(bd("6000.00"), r.months().get(0).income());
    }

    @Test
    void expensesRespectDateBoundariesSoftDeletesAndTheProfitabilityFilter() throws Exception {
        expense(propertyA, ExpenseCategory.UTILITIES, "1000.00", LocalDate.of(2026, 6, 30), "day before");
        expense(propertyA, ExpenseCategory.INSURANCE, "2000.00", JUL1, "first day");
        expense(propertyA, ExpenseCategory.OTHER, "500.00", JUL31, "last day");
        expense(propertyA, ExpenseCategory.OTHER, "700.00", LocalDate.of(2026, 8, 1), "day after");
        Expense gone = expense(propertyA, ExpenseCategory.REPAIRS, "900.00", LocalDate.of(2026, 7, 10), "to be deleted");
        ctx.expenses().delete(gone.getExpenseId(), "wrong property");
        expense(propertyB, ExpenseCategory.UTILITIES, "1000.00", LocalDate.of(2026, 7, 15), "other property");

        ProfitabilityReport all = julyReport(null);
        assertEquals(bd("3500.00"), all.expenses(), "2000 + 500 + 1000; boundaries in, outside dates and the deleted expense out");
        ProfitabilityReport onlyA = julyReport(propertyA.getPropertyId());
        assertEquals(bd("2500.00"), onlyA.expenses());
        assertEquals(1, onlyA.properties().size());
        assertEquals(bd("-2500.00"), onlyA.noi(), "no rent: NOI is negative");
        assertEquals(List.of(ExpenseCategory.INSURANCE, ExpenseCategory.OTHER), onlyA.categories().stream().map(c -> c.category()).toList());
    }

    @Test
    void maintenanceCostIsCountedOnceWhetherOrNotAnExpenseIsLinked() throws Exception {
        MaintenanceRequest r1 = resolvedRequest(propertyA, "5000.00", LocalDate.of(2026, 7, 20));
        resolvedRequest(propertyA, "300.00", LocalDate.of(2026, 8, 5)); // resolved outside July
        MaintenanceRequest open = new MaintenanceRequest();            // in progress with a cost: not completed work
        open.setPropertyId(propertyA.getPropertyId());
        open.setTitle("Still going");
        open.setPriority(MaintenancePriority.LOW);
        open.setReportedDate(LocalDate.of(2026, 7, 2));
        MaintenanceRequest started = ctx.maintenance().open(open);
        ctx.maintenance().changeStatus(started.getRequestId(), MaintenanceStatus.IN_PROGRESS, null);
        MaintenanceRequest withCost = ctx.maintenance().get(started.getRequestId());
        withCost.setCost(bd("800.00"));
        ctx.maintenance().update(withCost);

        // 1. cost on a resolved request with no expense record counts, flagged as such
        ProfitabilityReport first = julyReport(null);
        assertEquals(bd("5000.00"), first.expenses());
        assertEquals(bd("5000.00"), first.maintenanceFromRequests());
        assertEquals(ExpenseCategory.MAINTENANCE, first.categories().get(0).category());

        // 2. an expense linked to that request REPLACES the request's own cost: 5200, never 10200
        Expense linked = new Expense();
        linked.setPropertyId(propertyA.getPropertyId());
        linked.setMaintenanceRequestId(r1.getRequestId());
        linked.setCategory(ExpenseCategory.MAINTENANCE);
        linked.setAmount(bd("5200.00"));
        linked.setExpenseDate(LocalDate.of(2026, 7, 22));
        linked.setDescription("Plumber invoice");
        linked.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        Expense savedLink = ctx.expenses().create(linked);
        ProfitabilityReport second = julyReport(null);
        assertEquals(bd("5200.00"), second.expenses());
        assertEquals(bd("0.00"), second.maintenanceFromRequests());

        // 3. the same repair cannot be recorded twice
        Expense twice = new Expense();
        twice.setPropertyId(propertyA.getPropertyId());
        twice.setMaintenanceRequestId(r1.getRequestId());
        twice.setCategory(ExpenseCategory.REPAIRS);
        twice.setAmount(bd("1.00"));
        twice.setExpenseDate(LocalDate.of(2026, 7, 23));
        twice.setDescription("Second record of the same repair");
        twice.setPaymentMethod(PaymentMethod.CASH);
        assertEquals("EXP-03", assertThrows(BusinessRuleException.class, () -> ctx.expenses().create(twice)).getRuleId());
        assertTrue(ctx.expenses().linkableMaintenance(propertyA.getPropertyId(), null).stream().noneMatch(m -> m.requestId() == r1.getRequestId()),
                "a request that already has an active expense is no longer offered");

        // 4. deleting the expense falls back to the request's own cost (still once)
        ctx.expenses().delete(savedLink.getExpenseId(), "wrong amount");
        ProfitabilityReport third = julyReport(null);
        assertEquals(bd("5000.00"), third.expenses());
        assertEquals(bd("5000.00"), third.maintenanceFromRequests());
        assertTrue(ctx.expenses().linkableMaintenance(propertyA.getPropertyId(), null).stream().anyMatch(m -> m.requestId() == r1.getRequestId()),
                "after the soft delete the request can be linked again");
    }

    @Test
    void propertyComparisonMonthlyTrendAndTotalsAreConsistent() throws Exception {
        List<RentCharge> charges = leaseOnA();
        ctx.payments().recordPayment(charges.get(1).getChargeId(), bd("10000.00"), LocalDate.of(2026, 7, 5), PaymentMethod.UPI, null, null);
        ctx.payments().recordPayment(charges.get(2).getChargeId(), bd("6000.00"), LocalDate.of(2026, 8, 5), PaymentMethod.UPI, null, null);
        expense(propertyA, ExpenseCategory.PROPERTY_TAX, "2500.00", LocalDate.of(2026, 7, 9), "Tax");
        expense(propertyA, ExpenseCategory.UTILITIES, "400.00", LocalDate.of(2026, 8, 9), "Water");
        expense(propertyB, ExpenseCategory.MANAGEMENT_FEES, "1500.00", LocalDate.of(2026, 8, 20), "Agent");

        ProfitabilityReport r = ctx.profitability().report(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30), null);
        assertEquals(bd("16000.00"), r.rentReceived());
        assertEquals(bd("4400.00"), r.expenses());
        assertEquals(bd("11600.00"), r.noi());

        PropertyProfit a = r.properties().stream().filter(p -> p.propertyId() == propertyA.getPropertyId()).findFirst().orElseThrow();
        PropertyProfit b = r.properties().stream().filter(p -> p.propertyId() == propertyB.getPropertyId()).findFirst().orElseThrow();
        assertEquals(bd("16000.00"), a.rentReceived());
        assertEquals(bd("2900.00"), a.expenses());
        assertEquals(bd("13100.00"), a.noi());
        assertEquals(bd("-1500.00"), b.noi());
        assertEquals(propertyA.getPropertyId(), r.properties().get(0).propertyId(), "best NOI first");
        assertEquals(r.noi(), r.properties().stream().map(PropertyProfit::noi).reduce(BigDecimal.ZERO, BigDecimal::add));

        assertEquals(3, r.months().size());
        assertEquals(bd("10000.00"), r.months().get(0).income());
        assertEquals(bd("2500.00"), r.months().get(0).expenses());
        assertEquals(bd("6000.00"), r.months().get(1).income());
        assertEquals(bd("1900.00"), r.months().get(1).expenses());
        assertEquals(bd("0.00"), r.months().get(2).income());
        assertEquals(r.noi(), r.months().stream().map(m -> m.noi()).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void emptyDatabaseGivesAZeroReport() throws Exception {
        ProfitabilityReport r = ctx.profitability().report(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null);
        assertTrue(r.isEmpty());
        assertEquals(bd("0.00"), r.noi());
        assertEquals(12, r.months().size());
        assertTrue(r.categories().isEmpty());
        assertEquals(2, r.properties().size(), "the two active properties are listed with zeros");
    }
}
