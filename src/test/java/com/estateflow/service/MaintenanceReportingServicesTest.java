package com.estateflow.service;

import com.estateflow.support.TestAuth;
import com.estateflow.dao.*;
import com.estateflow.dto.*;
import com.estateflow.exception.*;
import com.estateflow.model.*;
import com.estateflow.model.enums.*;
import com.estateflow.report.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.estateflow.service.TestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MaintenanceReportingServicesTest {
    MaintenanceDao maintenanceDao = mock(MaintenanceDao.class);
    PropertyDao propertyDao = mock(PropertyDao.class);
    AgreementDao agreementDao = mock(AgreementDao.class);
    ReportingDao reportingDao = mock(ReportingDao.class);
    PaymentDao paymentDao = mock(PaymentDao.class);
    MaintenanceService maintenance;

    @BeforeEach
    void setUp() {
        maintenance = new MaintenanceService(maintenanceDao, propertyDao, agreementDao, reportingDao,
                new PassThroughTransactionManager(), AuditService.noop(), CLOCK, TestAuth.admin());
        when(maintenanceDao.insert(any(MaintenanceRequest.class))).thenAnswer(i -> {
            MaintenanceRequest r = i.getArgument(0);
            r.setRequestId(3L);
            return r;
        });
        when(maintenanceDao.update(any(MaintenanceRequest.class))).thenReturn(1);
        when(propertyDao.findById(1L)).thenReturn(Optional.of(property(1L, 1L, true)));
    }

    private MaintenanceRequest request() {
        MaintenanceRequest r = new MaintenanceRequest();
        r.setPropertyId(1L);
        r.setTitle("Leaking tap");
        r.setReportedDate(LocalDate.of(2026, 10, 5));
        return r;
    }

    private MaintenanceRequest stored(MaintenanceStatus status) {
        MaintenanceRequest r = request();
        r.setRequestId(3L);
        r.setStatus(status);
        r.setPriority(MaintenancePriority.HIGH);
        return r;
    }

    @Test
    @DisplayName("BR-30: new requests are OPEN with default priority")
    void open_valid_isOpenWithDefaultPriority() throws Exception {
        MaintenanceRequest saved = maintenance.open(request());
        assertEquals(MaintenanceStatus.OPEN, saved.getStatus());
        assertEquals(MaintenancePriority.MEDIUM, saved.getPriority());
    }

    @Test
    @DisplayName("BR-30: inactive property, future reported date and unrelated tenant are rejected")
    void open_ruleViolations() {
        when(propertyDao.findById(1L)).thenReturn(Optional.of(property(1L, 1L, false)));
        assertEquals("BR-30", assertThrows(BusinessRuleException.class, () -> maintenance.open(request())).getRuleId());
        when(propertyDao.findById(1L)).thenReturn(Optional.of(property(1L, 1L, true)));
        MaintenanceRequest future = request();
        future.setReportedDate(TODAY.plusDays(1));
        assertThrows(ValidationException.class, () -> maintenance.open(future));
        MaintenanceRequest withTenant = request();
        withTenant.setTenantId(9L);
        when(agreementDao.existsForPropertyAndTenant(1L, 9L)).thenReturn(false);
        assertEquals("BR-30", assertThrows(BusinessRuleException.class, () -> maintenance.open(withTenant)).getRuleId());
        when(agreementDao.existsForPropertyAndTenant(1L, 9L)).thenReturn(true);
        assertDoesNotThrow(() -> maintenance.open(withTenant));
    }

    @Test
    @DisplayName("BR-32: cost cannot be set on a new request or while OPEN")
    void cost_notAllowedWhenOpen() {
        MaintenanceRequest r = request();
        r.setCost(BigDecimal.TEN);
        assertEquals("BR-32", assertThrows(BusinessRuleException.class, () -> maintenance.open(r)).getRuleId());
        when(maintenanceDao.findById(3L)).thenReturn(Optional.of(stored(MaintenanceStatus.OPEN)));
        MaintenanceRequest edit = stored(MaintenanceStatus.OPEN);
        edit.setCost(BigDecimal.TEN);
        assertEquals("BR-32", assertThrows(BusinessRuleException.class, () -> maintenance.update(edit)).getRuleId());
    }

    @Test
    @DisplayName("BR-32: cost can be set while IN_PROGRESS and RESOLVED")
    void cost_allowedWhenInProgress() throws Exception {
        when(maintenanceDao.findById(3L)).thenReturn(Optional.of(stored(MaintenanceStatus.IN_PROGRESS)));
        MaintenanceRequest edit = stored(MaintenanceStatus.IN_PROGRESS);
        edit.setCost(new BigDecimal("250.00"));
        assertEquals(new BigDecimal("250.00"), maintenance.update(edit).getCost());
    }

    @Test
    @DisplayName("BR-31: only workflow transitions are allowed")
    void changeStatus_illegalTransition_throwsBr31() {
        when(maintenanceDao.findById(3L)).thenReturn(Optional.of(stored(MaintenanceStatus.OPEN)));
        assertEquals("BR-31", assertThrows(BusinessRuleException.class,
                () -> maintenance.changeStatus(3L, MaintenanceStatus.RESOLVED, null)).getRuleId());
        when(maintenanceDao.findById(3L)).thenReturn(Optional.of(stored(MaintenanceStatus.RESOLVED)));
        assertThrows(BusinessRuleException.class, () -> maintenance.changeStatus(3L, MaintenanceStatus.OPEN, null));
    }

    @Test
    @DisplayName("BR-31: RESOLVED sets resolved_date (default today, not before reported, not future)")
    void changeStatus_resolved_setsResolvedDate() throws Exception {
        MaintenanceRequest r = stored(MaintenanceStatus.IN_PROGRESS);
        when(maintenanceDao.findById(3L)).thenReturn(Optional.of(r));
        assertEquals(TODAY, maintenance.changeStatus(3L, MaintenanceStatus.RESOLVED, null).getResolvedDate());
        MaintenanceRequest r2 = stored(MaintenanceStatus.IN_PROGRESS);
        when(maintenanceDao.findById(3L)).thenReturn(Optional.of(r2));
        assertThrows(BusinessRuleException.class,
                () -> maintenance.changeStatus(3L, MaintenanceStatus.RESOLVED, LocalDate.of(2026, 10, 1)));
        assertThrows(BusinessRuleException.class,
                () -> maintenance.changeStatus(3L, MaintenanceStatus.RESOLVED, TODAY.plusDays(1)));
    }

    @Test
    @DisplayName("BR-31/32: cancelling clears the resolved date but refuses to hide a cost")
    void changeStatus_cancelled() throws Exception {
        MaintenanceRequest withCost = stored(MaintenanceStatus.IN_PROGRESS);
        withCost.setCost(BigDecimal.TEN);
        when(maintenanceDao.findById(3L)).thenReturn(Optional.of(withCost));
        assertEquals("BR-32", assertThrows(BusinessRuleException.class,
                () -> maintenance.changeStatus(3L, MaintenanceStatus.CANCELLED, null)).getRuleId());
        MaintenanceRequest open = stored(MaintenanceStatus.OPEN);
        when(maintenanceDao.findById(3L)).thenReturn(Optional.of(open));
        MaintenanceRequest done = maintenance.changeStatus(3L, MaintenanceStatus.CANCELLED, null);
        assertEquals(MaintenanceStatus.CANCELLED, done.getStatus());
        assertNull(done.getResolvedDate());
    }

    // ---- reporting
    @Test
    void dashboard_passesTodayAndMonthBoundsFromClock() {
        DashboardService dashboard = new DashboardService(reportingDao, CLOCK, 30, TestAuth.admin());
        dashboard.stats();
        verify(reportingDao).dashboardStats(TODAY, 30, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    }

    @Test
    void receipt_voidedPayment_throwsBr25_validPayment_building() throws Exception {
        ReceiptService receipts = new ReceiptService(paymentDao, reportingDao, new ReceiptWriter(), TestAuth.admin());
        Payment voided = new Payment(5L, new BigDecimal("100.00"), TODAY, PaymentMethod.CASH, null, null);
        voided.setPaymentId(1L);
        voided.setVoidedAt(LocalDateTime.of(2026, 10, 9, 0, 0));
        voided.setVoidReason("x");
        when(paymentDao.findById(1L)).thenReturn(Optional.of(voided));
        assertEquals("BR-25", assertThrows(BusinessRuleException.class, () -> receipts.buildReceipt(1L)).getRuleId());

        Payment valid = new Payment(5L, new BigDecimal("100.00"), TODAY, PaymentMethod.UPI, "R-9", null);
        valid.setPaymentId(2L);
        when(paymentDao.findById(2L)).thenReturn(Optional.of(valid));
        when(reportingDao.findReceiptContext(2L)).thenReturn(Optional.of(new ReceiptContext("Ravi", "Flat 4B",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), new BigDecimal("900.00"))));
        PaymentReceiptData data = receipts.buildReceipt(2L);
        assertEquals("RCP-2026-000002", data.receiptNumber());
        assertEquals(new BigDecimal("900.00"), data.currentBalance());
    }

    @Test
    void reports_exportPayments_writesCsvAndValidatesRange(@TempDir Path dir) throws Exception {
        ReportService reports = new ReportService(reportingDao, new CsvWriter(), CLOCK, TestAuth.admin());
        when(reportingDao.findPaymentRows(any(), any())).thenReturn(List.of(new PaymentReportRow(1L, "RCP-2026-000001",
                TODAY, "Ravi, K", "Flat \"4B\"", LocalDate.of(2026, 10, 1), new BigDecimal("100.00"),
                PaymentMethod.CASH, null, true, "typo")));
        Path file = dir.resolve("p.csv");
        assertEquals(1, reports.exportPayments(TODAY.minusDays(5), TODAY, file));
        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("\uFEFF"));
        assertTrue(text.contains("\"Ravi, K\""));
        assertTrue(text.contains("\"Flat \"\"4B\"\"\""));
        assertTrue(text.contains("VOID"));
        assertThrows(ValidationException.class, () -> reports.exportPayments(TODAY, TODAY.minusDays(1), file));
    }

    @Test
    void csvWriter_unwritablePath_throwsReportExportExceptionWithCause(@TempDir Path dir) {
        ReportExportException e = assertThrows(ReportExportException.class,
                () -> new CsvWriter().write(dir.resolve("missing/dir/x.csv"), new MaintenanceReport(), List.of()));
        assertNotNull(e.getCause());
    }

    @Test
    void receiptWriter_formatsAllFields() {
        PaymentReceiptData d = new PaymentReceiptData("RCP-2026-000002", TODAY, "Ravi", "Flat 4B",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), new BigDecimal("100.00"),
                PaymentMethod.UPI, null, new BigDecimal("900.00"));
        String text = new ReceiptWriter().format(d);
        assertTrue(text.contains("RCP-2026-000002") && text.contains("Ravi") && text.contains("Flat 4B"));
        assertTrue(text.contains("2026-10-01 to 2026-10-31"));
    }
}
