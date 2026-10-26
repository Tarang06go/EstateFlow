package com.estateflow.service;

import com.estateflow.support.TestAuth;
import com.estateflow.config.UserPreferences;
import com.estateflow.dao.*;
import com.estateflow.dto.*;
import com.estateflow.model.Owner;
import com.estateflow.model.Tenant;
import com.estateflow.model.enums.AgreementFilter;
import com.estateflow.model.enums.AgreementStatus;
import com.estateflow.report.CsvWriter;
import com.estateflow.report.ReportFormat;
import com.estateflow.util.UiFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static com.estateflow.service.TestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class V12ServicesTest {
    ReportingDao reportingDao = mock(ReportingDao.class);
    AgreementDao agreementDao = mock(AgreementDao.class);
    OwnerDao ownerDao = mock(OwnerDao.class);
    TenantDao tenantDao = mock(TenantDao.class);
    PropertyDao propertyDao = mock(PropertyDao.class);
    AuditDao auditDao = mock(AuditDao.class);

    // ---- dashboard overview
    @Test
    @DisplayName("Overview: window, expected vs collected series, capped action lists and recent activity")
    void dashboardOverview_composesRealQueries() {
        when(reportingDao.dashboardStats(any(), anyInt(), any(), any())).thenReturn(new DashboardStats(4, 3, 1,
                new BigDecimal("75.00"), 3, 1, BigDecimal.TEN, BigDecimal.ONE, 2, new java.util.EnumMap<>(com.estateflow.model.enums.MaintenancePriority.class)));
        when(reportingDao.monthlyCollections(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(List.of(new MonthlyAmount(YearMonth.of(2026, 9), new BigDecimal("500.00"))));
        when(reportingDao.monthlyExpectedRent(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(List.of(new MonthlyAmount(YearMonth.of(2026, 9), new BigDecimal("800.00"))));
        when(reportingDao.monthlyMaintenanceCosts(any(), any())).thenReturn(List.of());
        when(reportingDao.findRentReminders(TODAY, 0)).thenReturn(List.of(
                new RentReminderRow(1, 1, "P", "T", null, TODAY, TODAY.minusDays(9), BigDecimal.TEN, -9),
                new RentReminderRow(2, 1, "P", "T", null, TODAY, TODAY, BigDecimal.TEN, 0)));
        when(auditDao.isAvailable()).thenReturn(true);
        when(auditDao.search(isNull(), isNull(), isNull(), isNull(), anyBoolean(), eq(8))).thenReturn(List.of(new AuditEntry(1, java.time.LocalDateTime.of(2026, 10, 10, 8, 0),
                "PAYMENT", 3, "RECORDED", null, "{}", null, null)));
        DashboardService service = new DashboardService(reportingDao, agreementDao, new AuditService(auditDao, CLOCK, TestAuth.admin()), CLOCK, 30, TestAuth.admin());
        DashboardOverview o = service.overview(3);

        assertEquals(3, o.months());
        assertEquals(List.of(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)),
                o.collected().stream().map(MonthlyAmount::month).toList());
        assertEquals(new BigDecimal("500.00"), o.collected().get(1).amount());
        assertEquals(new BigDecimal("800.00"), o.expected().get(1).amount());
        assertEquals(1, o.overdue().size(), "due-today charges are not overdue");
        assertEquals(1, o.recentActivity().size());
        verify(agreementDao).findEndingBetween(TODAY, TODAY.plusDays(30));
        verify(reportingDao).findVacantPropertyRows(TODAY, 5);
        verify(reportingDao).findOpenMaintenanceRows(5);
    }

    @Test
    void dashboardOverview_clampsMonthsToSupportedRange() {
        when(reportingDao.dashboardStats(any(), anyInt(), any(), any())).thenReturn(null);
        DashboardService service = new DashboardService(reportingDao, agreementDao, AuditService.noop(), CLOCK, 30, TestAuth.admin());
        assertEquals(12, service.overview(99).months());
        assertEquals(3, service.overview(0).months());
        assertEquals(12, service.overview(99).expected().size());
    }

    // ---- profiles
    @Test
    @DisplayName("Owner and tenant profiles bundle real histories and today's outstanding rent")
    void profiles_useReportingQueries() throws Exception {
        Owner o = owner(1L, true);
        when(ownerDao.findById(1L)).thenReturn(Optional.of(o));
        when(reportingDao.outstandingForOwner(1L, TODAY)).thenReturn(new BigDecimal("12.00"));
        OwnerService owners = new OwnerService(ownerDao, propertyDao, reportingDao, new PassThroughTransactionManager(),
                AuditService.noop(), CLOCK, TestAuth.admin());
        OwnerDetails od = owners.details(1L);
        assertEquals(new BigDecimal("12.00"), od.outstandingBalance());
        verify(reportingDao).findPropertyRowsForOwner(1L, TODAY);

        Tenant t = tenant(2L, true);
        when(tenantDao.findById(2L)).thenReturn(Optional.of(t));
        when(reportingDao.outstandingForTenant(2L, TODAY)).thenReturn(new BigDecimal("30.00"));
        TenantService tenants = new TenantService(tenantDao, agreementDao, reportingDao, new PassThroughTransactionManager(),
                AuditService.noop(), CLOCK, TestAuth.admin());
        TenantDetails td = tenants.details(2L);
        assertEquals(new BigDecimal("30.00"), td.outstandingBalance());
        verify(agreementDao).findRowsByTenant(2L);
        verify(reportingDao).findPaymentRowsForTenant(2L);
        verify(reportingDao).findMaintenanceRowsForTenant(2L);
        assertThrows(com.estateflow.exception.EntityNotFoundException.class, () -> tenants.details(null));
    }

    // ---- audit of creates/updates never stores contact details
    @Test
    @DisplayName("Owner/tenant create and update are audited with the name only (no phone, e-mail or ID proof)")
    void createAndUpdate_auditedWithoutPersonalContactData() throws Exception {
        when(auditDao.isAvailable()).thenReturn(true);
        AuditService audit = new AuditService(auditDao, CLOCK, TestAuth.admin());
        when(ownerDao.findByPhone(any())).thenReturn(Optional.empty());
        when(ownerDao.insert(any(Owner.class))).thenAnswer(i -> {
            Owner x = i.getArgument(0);
            x.setId(5L);
            return x;
        });
        OwnerService owners = new OwnerService(ownerDao, propertyDao, reportingDao, new PassThroughTransactionManager(), audit, CLOCK, TestAuth.admin());
        owners.create(owner(null, true));
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(auditDao).insert(eq("OWNER"), eq(5L), eq("CREATED"), isNull(), json.capture(), any(), any(), any());
        assertEquals("{\"name\":\"Asha Verma\"}", json.getValue());
        assertFalse(json.getValue().contains("98765") || json.getValue().contains("@"));

        Owner stored = owner(5L, true);
        when(ownerDao.findById(5L)).thenReturn(Optional.of(stored));
        when(ownerDao.update(any(Owner.class))).thenReturn(1);
        Owner edited = owner(5L, true);
        edited.setFullName("Asha V. Verma");
        owners.update(edited);
        verify(auditDao).insert(eq("OWNER"), eq(5L), eq("UPDATED"), contains("Asha Verma"), contains("Asha V. Verma"), any(), any(), any());
    }

    // ---- reports
    @Test
    @DisplayName("Agreement and expiring-lease reports: filters/windows reach the DAO; CSV and PDF are written")
    void agreementReports_exportCsvAndPdf(@TempDir Path dir) throws Exception {
        when(agreementDao.searchRows("", AgreementFilter.ACTIVE, TODAY, 10_000)).thenReturn(List.of(new AgreementListRow(1, 2,
                "Flat 4B", 3, "Ravi", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 31),
                new BigDecimal("1500.00"), AgreementStatus.ACTIVE, null)));
        when(agreementDao.findEndingBetween(TODAY, TODAY.plusDays(30))).thenReturn(List.of(new ExpiringLeaseRow(1, "Flat 4B",
                "Ravi", LocalDate.of(2026, 10, 31), 21, new BigDecimal("1500.00"), false)));
        ReportService reports = new ReportService(reportingDao, new CsvWriter(), CLOCK, 7, agreementDao, 30, TestAuth.admin());
        assertEquals(1, reports.exportAgreements(AgreementFilter.ACTIVE, dir.resolve("a.csv"), ReportFormat.CSV));
        assertTrue(Files.readString(dir.resolve("a.csv")).contains("Flat 4B"));
        assertEquals(1, reports.exportAgreements(AgreementFilter.ACTIVE, dir.resolve("a.pdf"), ReportFormat.PDF));
        assertEquals(1, reports.exportExpiringLeases(dir.resolve("e.pdf"), ReportFormat.PDF));
        assertEquals(1, reports.exportExpiringLeases(dir.resolve("e.csv"), ReportFormat.CSV));
        for (String f : List.of("a.pdf", "e.pdf")) {
            assertEquals("%PDF-1.4", new String(Files.readAllBytes(dir.resolve(f)), 0, 8, java.nio.charset.StandardCharsets.ISO_8859_1));
        }
    }

    // ---- preferences
    @Test
    @DisplayName("Preferences persist to a file, reload, and fall back to defaults for damaged values")
    void preferences_roundTripAndFallback(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sub/prefs.properties");
        UserPreferences p = UserPreferences.load(file);
        assertEquals("\u20B9", p.currencySymbol());
        p.update("$", "yyyy-MM-dd", 12);
        p.save();
        UserPreferences again = UserPreferences.load(file);
        assertEquals("$", again.currencySymbol());
        assertEquals("yyyy-MM-dd", again.datePattern());
        assertEquals(12, again.dashboardMonths());

        Files.writeString(file, "currency.symbol=EUR!!\ndate.pattern=banana\ndashboard.months=7\n");
        UserPreferences damaged = UserPreferences.load(file);
        assertEquals("\u20B9", damaged.currencySymbol());
        assertEquals("dd MMM yyyy", damaged.datePattern());
        assertEquals(6, damaged.dashboardMonths());
        assertThrows(IllegalArgumentException.class, () -> damaged.update("EUR", "dd MMM yyyy", 6));
        assertThrows(IllegalArgumentException.class, () -> damaged.update("$", "dd MMM yyyy", 5));
        UserPreferences.defaults().save(); // in-memory: no file, no error
    }

    @Test
    void uiFormat_followsPreferences() {
        UserPreferences p = UserPreferences.defaults();
        UiFormat f = new UiFormat(p);
        assertEquals("\u20B9 1,234.50", f.money(new BigDecimal("1234.5")));
        assertEquals("10 Oct 2026", f.date(TODAY));
        p.update("", "dd/MM/yyyy", 6);
        assertEquals("1,234.50", f.money(new BigDecimal("1234.5")));
        assertEquals("10/10/2026", f.date(TODAY));
        assertEquals("", f.date(null));
        assertEquals("\u20B9 0.00", new UiFormat(UserPreferences.defaults()).money(null));
    }
}
