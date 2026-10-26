package com.estateflow.service;

import com.estateflow.dao.ReportingDao;
import com.estateflow.dao.TransactionManager;
import com.estateflow.dto.*;
import com.estateflow.dto.AdvancedAnalyticsReport.*;
import com.estateflow.exception.AuthorizationException;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.model.WorkOrderStatus;
import com.estateflow.model.enums.MaintenancePriority;
import com.estateflow.model.enums.MaintenanceStatus;
import com.estateflow.report.ReportFormat;
import com.estateflow.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvancedAnalyticsServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);
    @TempDir Path temp;
    private final ReportingDao reporting = mock(ReportingDao.class);
    private final ProfitabilityService profitability = mock(ProfitabilityService.class);
    private final MaintenanceService maintenance = mock(MaintenanceService.class);
    private final LeaseRenewalService leases = mock(LeaseRenewalService.class);
    private final WorkOrderService workOrders = mock(WorkOrderService.class);

    private AdvancedAnalyticsService service() {
        TransactionManager direct = new TransactionManager() {
            @Override public <T> T inTransaction(com.estateflow.dao.TransactionalWork<T> work) throws EstateFlowException {
                return work.execute();
            }
        };
        return new AdvancedAnalyticsService(reporting, profitability, maintenance, leases, workOrders,
                direct, new com.estateflow.report.CsvWriter(), CLOCK, 30, TestAuth.admin());
    }

    private ProfitabilityReport actual() {
        return new ProfitabilityReport(LocalDate.of(2026, 9, 1), TODAY, null, bd("1800"), bd("1500"), bd("400"),
                bd("1100"), BigDecimal.ZERO, List.of(), List.of(), List.of(
                new ProfitabilityReport.MonthlyProfit(YearMonth.of(2026, 9), bd("300"), bd("100")),
                new ProfitabilityReport.MonthlyProfit(YearMonth.of(2026, 10), bd("900"), bd("500"))));
    }

    private void prepare() throws Exception {
        when(profitability.report(any(), any(), isNull())).thenReturn(actual());
        when(reporting.findOccupancyRows(TODAY)).thenReturn(List.of(
                new PropertyOccupancyRow(1, "A", "City", "Owner", true, true, "Tenant", bd("500")),
                new PropertyOccupancyRow(2, "B", "City", "Owner", true, true, "Tenant", bd("600")),
                new PropertyOccupancyRow(3, "C", "City", "Owner", true, false, null, bd("700")),
                new PropertyOccupancyRow(4, "D", "City", "Owner", false, true, "Tenant", bd("800"))));
        when(reporting.collectionMetrics(any(), any())).thenReturn(new CollectionMetrics(10, 6, bd("5000"), bd("4000"), bd("1000"), bd("80.00")));
        when(reporting.arrearsAging(TODAY)).thenReturn(List.of(new ArrearsBucket("31-60 days", 2, bd("750"))));
        when(leases.endingWithinWarningWindow(30)).thenReturn(List.of(
                new ExpiringLeaseRow(1, "A", "T1", LocalDate.of(2026, 10, 20), 10, bd("1000"), false),
                new ExpiringLeaseRow(2, "B", "T2", LocalDate.of(2026, 11, 5), 26, bd("1500"), false)));
        when(maintenance.summary()).thenReturn(new MaintenanceSummary(Map.of(MaintenanceStatus.OPEN, 1),
                Map.of(MaintenancePriority.HIGH, 1), bd("100"), bd("50"), bd("3.0"), List.of(), List.of()));
        when(workOrders.vendorPerformance(any(), any())).thenReturn(List.of());
    }

    @Test void reportCombinesActualMetricsAndClearlyCalculatedForecast() throws Exception {
        prepare();
        AdvancedAnalyticsReport report = service().report(LocalDate.of(2026, 9, 1), TODAY);
        assertEquals(66.67, report.occupancy().percent().doubleValue());
        assertEquals(3, report.occupancy().activeProperties());
        assertEquals(2, report.arrears().get(1).charges());
        assertEquals("1-30 days", report.arrears().get(0).label());
        assertEquals(BigDecimal.ZERO, report.arrears().get(0).balance());
        assertEquals(2, report.leaseExpiry().leasesEnding());
        assertEquals(bd("2500"), report.leaseExpiry().monthlyRentAtRisk());
        assertEquals(6, report.forecast().size());
        assertEquals(YearMonth.of(2026, 11), report.forecast().get(0).month());
        assertEquals(bd("600.00"), report.forecast().get(0).estimatedRentReceived());
        assertEquals(bd("300.00"), report.forecast().get(0).estimatedOperatingExpenses());
        assertEquals(bd("300.00"), report.forecast().get(0).estimatedNoi());
    }

    @Test void csvExportIncludesActualAndForecastSections() throws Exception {
        prepare();
        Path path = temp.resolve("analytics.csv");
        service().export(LocalDate.of(2026, 9, 1), TODAY, path, ReportFormat.CSV);
        String contents = Files.readString(path);
        assertTrue(contents.contains("Rent collection"));
        assertTrue(contents.contains("Forecast estimate"));
        assertTrue(contents.contains("Vendor performance"));
    }

    @Test void vendorPerformanceDistinguishesUnavailableSchemaFromNoMatchingWork() throws Exception {
        prepare();
        AdvancedAnalyticsReport unavailable = service().report(LocalDate.of(2026, 9, 1), TODAY);
        assertFalse(unavailable.vendorAnalyticsAvailable());
        verify(workOrders, never()).vendorPerformance(any(), any());

        when(workOrders.isAvailable()).thenReturn(true);
        AdvancedAnalyticsReport availableButEmpty = service().report(LocalDate.of(2026, 9, 1), TODAY);
        assertTrue(availableButEmpty.vendorAnalyticsAvailable());
        assertTrue(availableButEmpty.vendorPerformance().isEmpty());
        verify(workOrders).vendorPerformance(LocalDate.of(2026, 9, 1), TODAY);
    }

    @Test void pdfExportCreatesAReadablePdfArtifact() throws Exception {
        prepare();
        Path path = temp.resolve("analytics.pdf");
        service().export(LocalDate.of(2026, 9, 1), TODAY, path, ReportFormat.PDF);
        assertTrue(new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.ISO_8859_1).startsWith("%PDF-1.4"));
    }

    @Test void invalidReportPeriodIsRejectedBeforeQueries() {
        assertThrows(com.estateflow.exception.ValidationException.class,
                () -> service().report(TODAY, TODAY.minusDays(1)));
        assertThrows(com.estateflow.exception.ValidationException.class,
                () -> service().report(LocalDate.of(2010, 1, 1), TODAY));
        verifyNoInteractions(reporting, profitability, maintenance, leases, workOrders);
    }

    @Test void signedOutUserIsRejectedBeforeReadingSources() {
        TransactionManager direct = new TransactionManager() {
            @Override public <T> T inTransaction(com.estateflow.dao.TransactionalWork<T> work) throws EstateFlowException { return work.execute(); }
        };
        AdvancedAnalyticsService secured = new AdvancedAnalyticsService(reporting, profitability, maintenance, leases,
                workOrders, direct, new com.estateflow.report.CsvWriter(), CLOCK, 30, TestAuth.signedOut());
        assertThrows(AuthorizationException.class, () -> secured.report(LocalDate.of(2026, 9, 1), TODAY));
        verifyNoInteractions(reporting, profitability, maintenance, leases, workOrders);
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
