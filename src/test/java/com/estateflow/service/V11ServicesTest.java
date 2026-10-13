package com.estateflow.service;

import com.estateflow.dao.*;
import com.estateflow.dto.*;
import com.estateflow.exception.ValidationException;
import com.estateflow.model.Owner;
import com.estateflow.model.Property;
import com.estateflow.model.enums.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.estateflow.service.TestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class V11ServicesTest {
    ReportingDao reportingDao = mock(ReportingDao.class);
    OwnerDao ownerDao = mock(OwnerDao.class);
    TenantDao tenantDao = mock(TenantDao.class);
    AgreementDao agreementDao = mock(AgreementDao.class);
    PropertyDao propertyDao = mock(PropertyDao.class);

    // ---- C. search
    @Test
    @DisplayName("Global search: fewer than 2 characters is rejected before any query runs")
    void search_tooShort_throwsValidation() {
        SearchService s = new SearchService(ownerDao, tenantDao, agreementDao, reportingDao, CLOCK);
        assertThrows(ValidationException.class, () -> s.search(" a "));
        assertThrows(ValidationException.class, () -> s.search(null));
        verifyNoMoreInteractions(ownerDao, tenantDao, agreementDao, reportingDao);
    }

    @Test
    @DisplayName("Global search: every entity type is queried with the same cap and today's date")
    void search_cappedQueriesPerType() throws Exception {
        SearchService s = new SearchService(ownerDao, tenantDao, agreementDao, reportingDao, CLOCK);
        SearchResults results = s.search("  asha ");
        verify(ownerDao).search("asha", SearchService.PER_TYPE_LIMIT);
        verify(tenantDao).search("asha", SearchService.PER_TYPE_LIMIT);
        verify(reportingDao).searchPropertyRows("asha", TODAY, SearchService.PER_TYPE_LIMIT);
        verify(agreementDao).searchRows("asha", AgreementFilter.ALL, TODAY, SearchService.PER_TYPE_LIMIT);
        verify(reportingDao).searchPaymentRows("asha", null, null, null, SearchService.PER_TYPE_LIMIT);
        assertEquals(0, results.total());
    }

    @Test
    void agreementAndPaymentSearch_passFiltersAndValidateRange() throws Exception {
        AgreementService agreements = new AgreementService(agreementDao, mock(RentChargeDao.class), mock(PaymentDao.class),
                propertyDao, tenantDao, new PassThroughTransactionManager(), AuditService.noop(), CLOCK, 30);
        agreements.searchRows("flat", AgreementFilter.EXPIRED);
        verify(agreementDao).searchRows("flat", AgreementFilter.EXPIRED, TODAY, 500);
        PaymentService payments = new PaymentService(mock(PaymentDao.class), mock(RentChargeDao.class), reportingDao,
                new PassThroughTransactionManager(), AuditService.noop(), CLOCK);
        payments.search("ravi", TODAY.minusDays(3), TODAY, Boolean.FALSE);
        verify(reportingDao).searchPaymentRows("ravi", TODAY.minusDays(3), TODAY, Boolean.FALSE, 500);
        assertThrows(ValidationException.class, () -> payments.search("", TODAY, TODAY.minusDays(1), null));
    }

    // ---- B. dashboard charts
    @Test
    @DisplayName("Dashboard charts: six calendar months ending this month, zero-filled, amounts in the right month")
    void dashboardCharts_zeroFilledSixMonths() {
        when(reportingDao.monthlyCollections(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(List.of(new MonthlyAmount(YearMonth.of(2026, 7), new BigDecimal("1500.00")),
                        new MonthlyAmount(YearMonth.of(2026, 10), new BigDecimal("200.00"))));
        when(reportingDao.monthlyMaintenanceCosts(any(), any())).thenReturn(List.of());
        DashboardCharts charts = new DashboardService(reportingDao, CLOCK, 30).charts();
        assertEquals(6, charts.rentCollected().size());
        assertEquals(YearMonth.of(2026, 5), charts.rentCollected().get(0).month());
        assertEquals(YearMonth.of(2026, 10), charts.rentCollected().get(5).month());
        assertEquals(new BigDecimal("1500.00"), charts.rentCollected().get(2).amount());
        assertEquals(BigDecimal.ZERO, charts.rentCollected().get(3).amount());
        assertTrue(MonthlyAmount.allZero(charts.maintenanceCosts()));
    }

    @Test
    void monthlyAmountFill_mergesDuplicatesAndCrossesYearBoundary() {
        List<MonthlyAmount> filled = MonthlyAmount.fill(List.of(new MonthlyAmount(YearMonth.of(2025, 12), BigDecimal.ONE),
                new MonthlyAmount(YearMonth.of(2025, 12), BigDecimal.TEN)), YearMonth.of(2026, 2), 3);
        assertEquals(List.of(YearMonth.of(2025, 12), YearMonth.of(2026, 1), YearMonth.of(2026, 2)),
                filled.stream().map(MonthlyAmount::month).toList());
        assertEquals(new BigDecimal("11"), filled.get(0).amount());
    }

    // ---- E. reminders
    @Test
    @DisplayName("Reminders: the configured window and today's date reach the query; categories follow the day count")
    void reminders_windowAndCategories() {
        RentReminderService service = new RentReminderService(reportingDao, CLOCK, 7);
        service.reminders();
        verify(reportingDao).findRentReminders(TODAY, 7);
        assertEquals(7, service.getWindowDays());
        assertEquals(RentReminderRow.Category.OVERDUE, row(-1).category());
        assertEquals(RentReminderRow.Category.DUE_TODAY, row(0).category());
        assertEquals(RentReminderRow.Category.UPCOMING, row(7).category());
    }

    private RentReminderRow row(long days) {
        return new RentReminderRow(1, 1, "P", "T", null, TODAY, TODAY.plusDays(days), BigDecimal.TEN, days);
    }

    // ---- F. maintenance analytics
    @Test
    void maintenanceSummary_zeroFillsMonthlyCosts_andTotalsCombine() {
        Map<MaintenanceStatus, Integer> byStatus = new EnumMap<>(MaintenanceStatus.class);
        byStatus.put(MaintenanceStatus.OPEN, 2);
        byStatus.put(MaintenanceStatus.RESOLVED, 3);
        MaintenanceSummary raw = new MaintenanceSummary(byStatus, new EnumMap<>(MaintenancePriority.class),
                new BigDecimal("700.00"), new BigDecimal("300.00"), new BigDecimal("2.5"), List.of(),
                List.of(new MonthlyAmount(YearMonth.of(2026, 9), new BigDecimal("700.00"))));
        when(reportingDao.maintenanceSummary(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 10, 31))).thenReturn(raw);
        MaintenanceService service = new MaintenanceService(mock(MaintenanceDao.class), propertyDao, agreementDao, reportingDao,
                new PassThroughTransactionManager(), AuditService.noop(), CLOCK);
        MaintenanceSummary summary = service.summary();
        assertEquals(6, summary.costByMonth().size());
        assertEquals(new BigDecimal("1000.00"), summary.totalCost());
        assertEquals(5, summary.totalRequests());
        assertEquals(new BigDecimal("700.00"), summary.costByMonth().get(4).amount());
    }

    // ---- A. property details
    @Test
    @DisplayName("Property details: current agreement is the one occupying today; cost counts only in-progress/resolved work")
    void propertyDetails_composesHistoryAndTotals() throws Exception {
        Property p = property(5L, 1L, true);
        when(propertyDao.findById(5L)).thenReturn(Optional.of(p));
        Owner o = owner(1L, true);
        when(ownerDao.findById(1L)).thenReturn(Optional.of(o));
        AgreementListRow past = agreementRow(1, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
        AgreementListRow current = agreementRow(2, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        when(agreementDao.findRowsByProperty(5L)).thenReturn(List.of(current, past));
        when(reportingDao.findMaintenanceRowsForProperty(5L)).thenReturn(List.of(
                maintRow(MaintenanceStatus.RESOLVED, "100.00"), maintRow(MaintenanceStatus.IN_PROGRESS, "50.00"),
                maintRow(MaintenanceStatus.CANCELLED, "999.00"), maintRow(MaintenanceStatus.OPEN, null)));
        when(reportingDao.outstandingForProperty(5L, TODAY)).thenReturn(new BigDecimal("42.00"));
        PropertyService service = new PropertyService(propertyDao, ownerDao, agreementDao, reportingDao,
                new PassThroughTransactionManager(), AuditService.noop(), CLOCK);
        PropertyDetails d = service.details(5L);
        assertEquals(2L, d.currentAgreement().agreementId());
        assertEquals(2, d.agreements().size());
        assertEquals(new BigDecimal("150.00"), d.maintenanceCost());
        assertEquals(new BigDecimal("42.00"), d.outstandingBalance());
        assertEquals(o.getFullName(), d.ownerName());
    }

    @Test
    void propertyDetails_noCurrentAgreement_isNull() throws Exception {
        when(propertyDao.findById(5L)).thenReturn(Optional.of(property(5L, 1L, true)));
        when(ownerDao.findById(1L)).thenReturn(Optional.empty());
        when(agreementDao.findRowsByProperty(5L)).thenReturn(List.of(
                agreementRow(1, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31))));
        PropertyService service = new PropertyService(propertyDao, ownerDao, agreementDao, reportingDao,
                new PassThroughTransactionManager(), AuditService.noop(), CLOCK);
        PropertyDetails d = service.details(5L);
        assertNull(d.currentAgreement());
        assertEquals("", d.ownerName());
        assertEquals(BigDecimal.ZERO, d.maintenanceCost());
    }

    private AgreementListRow agreementRow(long id, LocalDate start, LocalDate end) {
        return new AgreementListRow(id, 5, "Flat", 2, "Ravi", start, end, end, BigDecimal.TEN, AgreementStatus.ACTIVE, null);
    }

    private MaintenanceReportRow maintRow(MaintenanceStatus status, String cost) {
        return new MaintenanceReportRow(1, 5, "Flat", null, "t", MaintenancePriority.LOW, status, TODAY, null, null,
                cost == null ? null : new BigDecimal(cost));
    }
}
