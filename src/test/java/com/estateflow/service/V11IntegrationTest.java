package com.estateflow.service;

import com.estateflow.support.TestAuth;
import com.estateflow.dao.AuditDao;
import com.estateflow.dao.jdbc.*;
import com.estateflow.db.DatabaseManager;
import com.estateflow.db.JdbcTransactionManager;
import com.estateflow.dto.*;
import com.estateflow.exception.DataAccessException;
import com.estateflow.model.*;
import com.estateflow.model.enums.*;
import com.estateflow.report.ReportFormat;
import com.estateflow.support.Fixtures;
import com.estateflow.support.IntegrationTestBase;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** v1.1 features against real MySQL (search, reminders, charts, analytics, details, audit, PDF export). */
class V11IntegrationTest extends IntegrationTestBase {

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private RentalAgreement agreement(Property p, Tenant t, LocalDate start, int months, String rent) throws Exception {
        return ctx.agreements().create(p.getPropertyId(), t.getId(), start, months, bd(rent), null, null);
    }

    // ---- C. search and filters
    @Test
    @DisplayName("Global search is capped per type, finds payments by receipt number, and matches names/phones")
    void globalSearch_isCappedAndFindsByReceiptNumber() throws Exception {
        for (int i = 0; i < 20; i++) {
            ctx.owners().create(new Owner("Searchable Owner " + i, "9100000" + String.format("%03d", i), null, null));
        }
        assertEquals(SearchService.PER_TYPE_LIMIT, ctx.search().search("Searchable").owners().size());

        Property property = Fixtures.property(ctx, Fixtures.owner(ctx));
        Tenant tenant = Fixtures.tenant(ctx);
        RentalAgreement a = agreement(property, tenant, LocalDate.of(2026, 10, 1), 2, "1000.00");
        long charge = ctx.payments().findCharges(a.getAgreementId()).get(0).getChargeId();
        Payment p = ctx.payments().recordPayment(charge, bd("100.00"), TODAY, PaymentMethod.CASH, "REF-ABC", null);

        SearchResults byReceipt = ctx.search().search(p.receiptNumber());
        assertEquals(1, byReceipt.payments().size());
        assertEquals(p.getPaymentId(), byReceipt.payments().get(0).paymentId());
        assertEquals(1, ctx.search().search("REF-ABC").payments().size());
        assertEquals(1, ctx.search().search(tenant.getFullName()).tenants().size());
        assertEquals(1, ctx.search().search(property.getName()).properties().size());
        assertEquals(1, ctx.search().search(property.getName()).agreements().size());
    }

    @Test
    @DisplayName("Agreement filters use the Section 5.4 date logic; payments filter by date and void status")
    void filters_agreementsAndPayments() throws Exception {
        Tenant t = Fixtures.tenant(ctx);
        Property active = Fixtures.property(ctx, Fixtures.owner(ctx));
        Property upcoming = Fixtures.property(ctx, Fixtures.owner(ctx));
        Property expired = Fixtures.property(ctx, Fixtures.owner(ctx));
        Property terminated = Fixtures.property(ctx, Fixtures.owner(ctx));
        agreement(active, t, LocalDate.of(2026, 9, 1), 6, "1000.00");
        agreement(upcoming, t, LocalDate.of(2026, 12, 1), 6, "1000.00");
        agreement(expired, t, LocalDate.of(2026, 1, 1), 6, "1000.00");
        RentalAgreement term = agreement(terminated, t, LocalDate.of(2026, 1, 1), 12, "1000.00");
        ctx.agreements().terminate(term.getAgreementId(), LocalDate.of(2026, 6, 30));

        assertEquals(List.of(active.getName()), names(ctx.agreements().searchRows("", AgreementFilter.ACTIVE)));
        assertEquals(List.of(upcoming.getName()), names(ctx.agreements().searchRows("", AgreementFilter.UPCOMING)));
        assertEquals(List.of(expired.getName()), names(ctx.agreements().searchRows("", AgreementFilter.EXPIRED)));
        assertEquals(List.of(terminated.getName()), names(ctx.agreements().searchRows("", AgreementFilter.TERMINATED)));
        assertEquals(4, ctx.agreements().searchRows("", AgreementFilter.ALL).size());
        assertEquals(1, ctx.agreements().searchRows(active.getName(), AgreementFilter.ALL).size());
        assertEquals(0, ctx.agreements().searchRows(active.getName(), AgreementFilter.EXPIRED).size());

        long chargeId = ctx.payments().findCharges(term.getAgreementId()).get(0).getChargeId();
        Payment early = ctx.payments().recordPayment(chargeId, bd("100.00"), LocalDate.of(2026, 9, 5), PaymentMethod.CASH, null, null);
        Payment late = ctx.payments().recordPayment(chargeId, bd("200.00"), LocalDate.of(2026, 10, 5), PaymentMethod.UPI, null, null);
        ctx.payments().voidPayment(late.getPaymentId(), "wrong tenant");
        assertEquals(2, ctx.payments().search("", null, null, null).size());
        assertEquals(1, ctx.payments().search("", null, null, Boolean.TRUE).size());
        assertEquals(early.getPaymentId(), ctx.payments().search("", null, null, Boolean.FALSE).get(0).paymentId());
        assertEquals(1, ctx.payments().search("", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null).size());
        assertEquals(0, ctx.payments().search("nobody-by-this-name", null, null, null).size());
    }

    private static List<String> names(List<AgreementListRow> rows) {
        return rows.stream().map(AgreementListRow::propertyName).toList();
    }

    // ---- E. reminders
    @Test
    @DisplayName("Reminders: overdue, due today and in-window charges once each; paid, cancelled and far-future excluded")
    void rentReminders_noDuplicatesNoStaleEntries() throws Exception {
        Tenant t = Fixtures.tenant(ctx);
        Property overdueP = Fixtures.property(ctx, Fixtures.owner(ctx));
        Property todayP = Fixtures.property(ctx, Fixtures.owner(ctx));
        Property soonP = Fixtures.property(ctx, Fixtures.owner(ctx));
        Property paidP = Fixtures.property(ctx, Fixtures.owner(ctx));
        Property cancelledP = Fixtures.property(ctx, Fixtures.owner(ctx));
        RentalAgreement overdue = agreement(overdueP, t, LocalDate.of(2026, 10, 1), 3, "1000.00");   // Oct 1 overdue; Nov 1 outside 7 days
        agreement(todayP, t, LocalDate.of(2026, 10, 10), 2, "2000.00");                              // due today
        agreement(soonP, t, LocalDate.of(2026, 10, 15), 2, "3000.00");                               // due in 5 days
        RentalAgreement paid = agreement(paidP, t, LocalDate.of(2026, 10, 1), 1, "500.00");
        RentalAgreement cancelled = agreement(cancelledP, t, LocalDate.of(2026, 9, 1), 6, "700.00"); // Sep, Oct due; later cancelled
        ctx.payments().recordPayment(ctx.payments().findCharges(paid.getAgreementId()).get(0).getChargeId(),
                bd("500.00"), TODAY, PaymentMethod.CASH, null, null);
        ctx.agreements().terminate(cancelled.getAgreementId(), LocalDate.of(2026, 9, 30)); // cancels the Oct 1 charge onwards

        List<RentReminderRow> rows = ctx.reminders().reminders();
        assertEquals(rows.size(), rows.stream().map(RentReminderRow::chargeId).distinct().count(), "one row per charge");
        assertEquals(List.of(overdueP.getName(), cancelledP.getName(), todayP.getName(), soonP.getName()).stream().sorted().toList(),
                rows.stream().map(RentReminderRow::propertyName).sorted().toList());
        RentReminderRow today = rows.stream().filter(r -> r.propertyName().equals(todayP.getName())).findFirst().orElseThrow();
        assertEquals(RentReminderRow.Category.DUE_TODAY, today.category());
        RentReminderRow soon = rows.stream().filter(r -> r.propertyName().equals(soonP.getName())).findFirst().orElseThrow();
        assertEquals(5, soon.daysUntilDue());
        assertEquals(RentReminderRow.Category.UPCOMING, soon.category());
        RentReminderRow late = rows.stream().filter(r -> r.propertyName().equals(overdueP.getName())).findFirst().orElseThrow();
        assertEquals(-9, late.daysUntilDue());
        assertEquals(rows.get(0).dueDate(), rows.stream().map(RentReminderRow::dueDate).min(LocalDate::compareTo).orElseThrow(),
                "oldest due date first");

        // a partial payment shrinks the balance shown; the remainder keeps the charge on the list
        long lateCharge = late.chargeId();
        ctx.payments().recordPayment(lateCharge, bd("400.00"), TODAY, PaymentMethod.UPI, null, null);
        assertEquals(bd("600.00"), ctx.reminders().reminders().stream().filter(r -> r.chargeId() == lateCharge)
                .findFirst().orElseThrow().balance());
        assertNotNull(overdue);
    }

    // ---- B / F. charts and analytics
    @Test
    @DisplayName("Dashboard charts and maintenance analytics aggregate real rows: voided payments and uncosted work excluded")
    void chartsAndAnalytics_useRealRecords() throws Exception {
        Property p = Fixtures.property(ctx, Fixtures.owner(ctx));
        Tenant t = Fixtures.tenant(ctx);
        RentalAgreement a = agreement(p, t, LocalDate.of(2026, 8, 1), 3, "1000.00");
        List<RentCharge> charges = ctx.payments().findCharges(a.getAgreementId());
        ctx.payments().recordPayment(charges.get(0).getChargeId(), bd("1000.00"), LocalDate.of(2026, 8, 3), PaymentMethod.CASH, null, null);
        Payment sep = ctx.payments().recordPayment(charges.get(1).getChargeId(), bd("600.00"), LocalDate.of(2026, 9, 3), PaymentMethod.CASH, null, null);
        ctx.payments().recordPayment(charges.get(1).getChargeId(), bd("400.00"), LocalDate.of(2026, 9, 20), PaymentMethod.UPI, null, null);
        Payment voided = ctx.payments().recordPayment(charges.get(2).getChargeId(), bd("250.00"), TODAY, PaymentMethod.CASH, null, null);
        ctx.payments().voidPayment(voided.getPaymentId(), "entered twice");

        DashboardCharts charts = ctx.dashboard().charts();
        assertEquals(6, charts.rentCollected().size());
        assertEquals(YearMonth.of(2026, 10), charts.rentCollected().get(5).month());
        assertEquals(bd("1000.00"), amountOf(charts.rentCollected(), YearMonth.of(2026, 8)));
        assertEquals(bd("1000.00"), amountOf(charts.rentCollected(), YearMonth.of(2026, 9)));
        assertEquals(0, amountOf(charts.rentCollected(), YearMonth.of(2026, 10)).signum(), "voided payment excluded");
        assertEquals(0, amountOf(charts.rentCollected(), YearMonth.of(2026, 5)).signum());
        assertTrue(MonthlyAmount.allZero(charts.maintenanceCosts()));

        MaintenanceRequest r1 = request(p, "Resolved job", LocalDate.of(2026, 9, 1));
        ctx.maintenance().changeStatus(r1.getRequestId(), MaintenanceStatus.IN_PROGRESS, null);
        r1.setCost(bd("300.00"));
        ctx.maintenance().update(r1);
        ctx.maintenance().changeStatus(r1.getRequestId(), MaintenanceStatus.RESOLVED, LocalDate.of(2026, 9, 5)); // 4 days
        MaintenanceRequest r2 = request(p, "Second resolved", LocalDate.of(2026, 10, 1));
        ctx.maintenance().changeStatus(r2.getRequestId(), MaintenanceStatus.IN_PROGRESS, null);
        r2.setCost(bd("100.00"));
        ctx.maintenance().update(r2);
        ctx.maintenance().changeStatus(r2.getRequestId(), MaintenanceStatus.RESOLVED, LocalDate.of(2026, 10, 3)); // 2 days
        MaintenanceRequest r3 = request(p, "In progress", LocalDate.of(2026, 10, 6));
        ctx.maintenance().changeStatus(r3.getRequestId(), MaintenanceStatus.IN_PROGRESS, null);
        r3.setCost(bd("50.00"));
        ctx.maintenance().update(r3);
        request(p, "Still open", LocalDate.of(2026, 10, 7));

        MaintenanceSummary s = ctx.maintenance().summary();
        assertEquals(2, s.countByStatus().get(MaintenanceStatus.RESOLVED));
        assertEquals(1, s.countByStatus().get(MaintenanceStatus.OPEN));
        assertEquals(1, s.countByStatus().get(MaintenanceStatus.IN_PROGRESS));
        assertEquals(4, s.totalRequests());
        assertEquals(bd("400.00"), s.resolvedCost());
        assertEquals(bd("50.00"), s.inProgressCost());
        assertEquals(bd("450.00"), s.totalCost());
        assertEquals(bd("3.0"), s.averageResolutionDays());
        assertEquals(2, s.openByPriority().get(MaintenancePriority.MEDIUM));
        assertEquals(1, s.costByProperty().size());
        assertEquals(bd("450.00"), s.costByProperty().get(0).cost());
        assertEquals(3, s.costByProperty().get(0).requests());
        assertEquals(bd("300.00"), amountOf(s.costByMonth(), YearMonth.of(2026, 9)));
        assertEquals(bd("150.00"), amountOf(s.costByMonth(), YearMonth.of(2026, 10)));
        assertEquals(bd("150.00"), amountOf(ctx.dashboard().charts().maintenanceCosts(), YearMonth.of(2026, 10)));
        assertNotNull(sep);
    }

    private MaintenanceRequest request(Property p, String title, LocalDate reported) throws Exception {
        MaintenanceRequest r = new MaintenanceRequest();
        r.setPropertyId(p.getPropertyId());
        r.setTitle(title);
        r.setReportedDate(reported);
        return ctx.maintenance().open(r);
    }

    private static BigDecimal amountOf(List<MonthlyAmount> list, YearMonth month) {
        return list.stream().filter(m -> m.month().equals(month)).findFirst().orElseThrow().amount();
    }

    @Test
    @DisplayName("Empty database: analytics and charts are zero-filled, not errors")
    void emptyDatabase_isHandled() {
        assertTrue(MonthlyAmount.allZero(ctx.dashboard().charts().rentCollected()));
        MaintenanceSummary s = ctx.maintenance().summary();
        assertEquals(0, s.totalRequests());
        assertNull(s.averageResolutionDays());
        assertTrue(s.costByProperty().isEmpty());
        assertEquals(6, s.costByMonth().size());
        assertTrue(ctx.reminders().reminders().isEmpty());
    }

    // ---- A. property details
    @Test
    @DisplayName("Property details: owner, current tenant, histories and outstanding balance from real records")
    void propertyDetails_realRecords() throws Exception {
        Owner owner = Fixtures.owner(ctx);
        Property p = Fixtures.property(ctx, owner);
        Tenant t = Fixtures.tenant(ctx);
        RentalAgreement old = agreement(p, t, LocalDate.of(2025, 1, 1), 12, "900.00");
        RentalAgreement current = agreement(p, t, LocalDate.of(2026, 1, 1), 12, "1000.00");
        long octCharge = ctx.payments().findCharges(current.getAgreementId()).get(9).getChargeId();
        ctx.payments().recordPayment(octCharge, bd("400.00"), TODAY, PaymentMethod.UPI, null, null);
        request(p, "Leak", LocalDate.of(2026, 10, 2));

        PropertyDetails d = ctx.properties().details(p.getPropertyId());
        assertEquals(owner.getFullName(), d.ownerName());
        assertEquals(current.getAgreementId(), d.currentAgreement().agreementId());
        assertEquals(t.getFullName(), d.currentAgreement().tenantName());
        assertEquals(2, d.agreements().size());
        assertEquals(1, d.payments().size());
        assertEquals(1, d.maintenance().size());
        // 2025 lease: 12 charges of 900 unpaid; 2026 lease: Jan-Oct due (10 x 1000) minus 400 paid
        assertEquals(bd("10800.00").add(bd("9600.00")), d.outstandingBalance());
        assertEquals(0, d.maintenanceCost().signum());
        assertNotNull(old);
    }

    // ---- G. audit
    @Test
    @DisplayName("Audit entries are written for the key operations, are valid JSON, and carry UTC timestamps")
    void audit_recordsKeyOperations() throws Exception {
        Assumptions.assumeTrue(ctx.audit().isEnabled(), "v1.1 audit_log table not present: apply db/migrations/v1.1_audit_log.sql");
        Owner owner = Fixtures.owner(ctx);
        Property p = Fixtures.property(ctx, owner);
        Tenant t = Fixtures.tenant(ctx);
        RentalAgreement a = agreement(p, t, LocalDate.of(2026, 9, 1), 6, "1000.00");
        long charge = ctx.payments().findCharges(a.getAgreementId()).get(0).getChargeId();
        Payment pay = ctx.payments().recordPayment(charge, bd("100.00"), TODAY, PaymentMethod.CASH, null, null);
        ctx.payments().voidPayment(pay.getPaymentId(), "typo \"quoted\"");
        ctx.agreements().terminate(a.getAgreementId(), LocalDate.of(2026, 10, 5)); // effective end in the past: deactivation allowed (BR-04)
        MaintenanceRequest m = request(p, "Audit me", LocalDate.of(2026, 10, 2)); // BR-30: needs an active property
        ctx.maintenance().changeStatus(m.getRequestId(), MaintenanceStatus.CANCELLED, null);
        ctx.properties().deactivate(p.getPropertyId());

        assertEquals(List.of("CREATED", "TERMINATED"), actions("AGREEMENT"));
        assertEquals(List.of("RECORDED", "VOIDED"), actions("PAYMENT"));
        assertEquals(List.of("CREATED", "DEACTIVATED"), actions("PROPERTY")); // v1.2 audits property create
        assertEquals(List.of("CREATED", "STATUS_CHANGED"), actions("MAINTENANCE")); // v1.2 audits maintenance create
        AuditEntry voidEntry = ctx.audit().find("PAYMENT", pay.getPaymentId(), 10).get(0);
        assertEquals(LocalDateTime.of(2026, 10, 10, 8, 0), voidEntry.createdAt());
        assertTrue(voidEntry.newValue().contains("typo"));
        assertEquals(pay.getPaymentId(), voidEntry.entityId());
        DatabaseManager db = ctx.databaseManager();
        try (var h = db.acquire(); var st = h.connection().createStatement();
             var rs = st.executeQuery("SELECT COUNT(*) FROM audit_log WHERE (old_value IS NOT NULL AND NOT JSON_VALID(old_value)) "
                     + "OR (new_value IS NOT NULL AND NOT JSON_VALID(new_value))")) {
            rs.next();
            assertEquals(0, rs.getInt(1));
        }
    }

    private List<String> actions(String type) {
        List<AuditEntry> entries = ctx.audit().find(type, null, 50);
        return entries.stream().sorted(java.util.Comparator.comparingLong(AuditEntry::auditId))
                .map(AuditEntry::action).toList();
    }

    /** An audit DAO that always fails on insert: the audited operation must roll back with it. */
    static class FailingAuditDao implements AuditDao {
        public boolean isAvailable() { return true; }
        public boolean supportsActor() { return true; }
        public void insert(String t, long id, String a, String o, String n, LocalDateTime at, Long actorId, String actor) {
            throw new DataAccessException("simulated audit failure", new java.sql.SQLException("boom", "HY000"));
        }
        public List<AuditEntry> find(String t, Long id, int limit) { return List.of(); }
        public List<AuditEntry> search(String t, Long id, String action, String actor, boolean userEvents, int limit) { return List.of(); }
    }

    @Test
    @DisplayName("A failing audit insert rolls the whole operation back (payment, agreement) - nothing is half-recorded")
    void auditFailure_rollsBackOperation() throws Exception {
        Property p = Fixtures.property(ctx, Fixtures.owner(ctx));
        Tenant t = Fixtures.tenant(ctx);
        RentalAgreement good = agreement(p, t, LocalDate.of(2026, 10, 1), 1, "1000.00");
        long charge = ctx.payments().findCharges(good.getAgreementId()).get(0).getChargeId();

        DatabaseManager db = ctx.databaseManager();
        AuditService failing = new AuditService(new FailingAuditDao(), CLOCK, TestAuth.admin());
        JdbcTransactionManager tx = new JdbcTransactionManager(db);
        PaymentService payments = new PaymentService(new JdbcPaymentDao(db), new JdbcRentChargeDao(db),
                new JdbcReportingDao(db), tx, failing, CLOCK, TestAuth.admin());
        assertThrows(DataAccessException.class,
                () -> payments.recordPayment(charge, bd("100.00"), TODAY, PaymentMethod.CASH, null, null));
        assertEquals(0, ctx.payments().findPayments(charge).size(), "payment must not survive a failed audit insert");

        AgreementService agreements = new AgreementService(new JdbcAgreementDao(db), new JdbcRentChargeDao(db),
                new JdbcPaymentDao(db), new JdbcPropertyDao(db), new JdbcTenantDao(db), tx, failing, CLOCK, 30, TestAuth.admin());
        Property other = Fixtures.property(ctx, Fixtures.owner(ctx));
        assertThrows(DataAccessException.class, () -> agreements.create(other.getPropertyId(), t.getId(),
                LocalDate.of(2026, 11, 1), 3, bd("500.00"), null, null));
        assertEquals(0, ctx.agreements().findByProperty(other.getPropertyId()).size());
    }

    // ---- D. PDF
    @Test
    @DisplayName("PDF receipts and reports are written from real data; CSV exports still work")
    void pdfAndCsvExports(@TempDir Path dir) throws Exception {
        Property p = Fixtures.property(ctx, Fixtures.owner(ctx));
        Tenant t = Fixtures.tenant(ctx);
        RentalAgreement a = agreement(p, t, LocalDate.of(2026, 9, 1), 2, "1000.00");
        long charge = ctx.payments().findCharges(a.getAgreementId()).get(0).getChargeId();
        Payment pay = ctx.payments().recordPayment(charge, bd("400.00"), TODAY, PaymentMethod.UPI, "UPI-1", null);

        Path receipt = dir.resolve("receipt.pdf");
        ctx.receipts().writeReceipt(pay.getPaymentId(), receipt);
        assertPdf(receipt, 1000);
        Path text = dir.resolve("receipt.txt");
        ctx.receipts().writeReceipt(pay.getPaymentId(), text);
        assertTrue(Files.readString(text).contains(pay.receiptNumber()));

        assertEquals(1, ctx.reports().exportPayments(TODAY.minusDays(30), TODAY, dir.resolve("p.pdf"), ReportFormat.PDF));
        assertPdf(dir.resolve("p.pdf"), 1000);
        assertEquals(1, ctx.reports().exportPayments(TODAY.minusDays(30), TODAY, dir.resolve("p.csv")));
        assertTrue(Files.readString(dir.resolve("p.csv")).contains(pay.receiptNumber()));
        assertTrue(ctx.reports().exportOverdue(dir.resolve("o.pdf"), ReportFormat.PDF) >= 1);
        assertEquals(1, ctx.reports().exportOccupancy(dir.resolve("occ.pdf"), ReportFormat.PDF));
        assertEquals(0, ctx.reports().exportMaintenance(null, dir.resolve("m.pdf"), ReportFormat.PDF));
        assertTrue(ctx.reports().exportRentReminders(dir.resolve("r.pdf"), ReportFormat.PDF) >= 1);
        assertTrue(ctx.reports().exportRentReminders(dir.resolve("r.csv"), ReportFormat.CSV) >= 1);
        for (String f : List.of("o.pdf", "occ.pdf", "m.pdf", "r.pdf")) {
            assertPdf(dir.resolve(f), 1000);
        }
    }

    private static void assertPdf(Path file, int minSize) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        assertTrue(bytes.length > minSize, file + " too small: " + bytes.length);
        assertEquals("%PDF-1.4", new String(bytes, 0, 8, StandardCharsets.ISO_8859_1));
        assertTrue(new String(bytes, StandardCharsets.ISO_8859_1).trim().endsWith("%%EOF"));
    }
}
