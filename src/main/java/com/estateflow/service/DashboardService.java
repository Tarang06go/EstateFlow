package com.estateflow.service;

import com.estateflow.security.Authorizer;
import com.estateflow.security.Permission;
import com.estateflow.dao.ReportingDao;
import com.estateflow.dao.AgreementDao;
import com.estateflow.dto.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public class DashboardService {

    private final ReportingDao reportingDao;
    private final Clock clock;
    private final int warningDays;
    private final AgreementDao agreementDao;
    private final AuditService audit;

    private final Authorizer auth;

    public DashboardService(ReportingDao reportingDao, Clock clock, int warningDays, Authorizer auth) {
        this(reportingDao, null, AuditService.noop(), clock, warningDays, auth);
    }

    public DashboardService(ReportingDao reportingDao, AgreementDao agreementDao, AuditService audit, Clock clock,
                            int warningDays, Authorizer auth) {
        this.auth = auth;
        this.reportingDao = reportingDao;
        this.agreementDao = agreementDao;
        this.audit = audit;
        this.clock = clock;
        this.warningDays = warningDays;
    }

    /**
     * Dashboard data for the last {@code months} calendar months (3, 6 or 12; other values are clamped).
     * Lists are short "action required" extracts: oldest overdue rent first, soonest-ending leases,
     * vacant active properties, most urgent open maintenance, and the latest audit entries.
     */
    public DashboardOverview overview(int months) {
        auth.require(Permission.VIEW_DATA);
        int span = Math.max(3, Math.min(12, months));
        LocalDate today = LocalDate.now(clock);
        YearMonth thisMonth = YearMonth.from(today);
        LocalDate from = thisMonth.minusMonths(span - 1L).atDay(1);
        LocalDate to = thisMonth.atEndOfMonth();
        List<RentReminderRow> overdue = reportingDao.findRentReminders(today, 0).stream()
                .filter(r -> r.category() == RentReminderRow.Category.OVERDUE).limit(LIST_LIMIT).toList();
        List<ExpiringLeaseRow> expiring = agreementDao.findEndingBetween(today, today.plusDays(warningDays)).stream()
                .limit(LIST_LIMIT).toList();
        return new DashboardOverview(stats(), span,
                MonthlyAmount.fill(reportingDao.monthlyCollections(from, to), thisMonth, span),
                MonthlyAmount.fill(reportingDao.monthlyExpectedRent(from, to), thisMonth, span),
                MonthlyAmount.fill(reportingDao.monthlyMaintenanceCosts(from, to), thisMonth, span),
                overdue, expiring, reportingDao.findVacantPropertyRows(today, LIST_LIMIT),
                reportingDao.findOpenMaintenanceRows(LIST_LIMIT), auth.can(Permission.VIEW_AUDIT) ? audit.find(null, null, 8) : java.util.List.of());
    }

    static final int LIST_LIMIT = 5;

    /** Last six calendar months of collections and maintenance cost, zero-filled for months without data. */
    public DashboardCharts charts() {
        auth.require(Permission.VIEW_DATA);
        YearMonth thisMonth = YearMonth.from(LocalDate.now(clock));
        LocalDate from = thisMonth.minusMonths(5).atDay(1);
        LocalDate to = thisMonth.atEndOfMonth();
        return new DashboardCharts(
                MonthlyAmount.fill(reportingDao.monthlyCollections(from, to), thisMonth, 6),
                MonthlyAmount.fill(reportingDao.monthlyMaintenanceCosts(from, to), thisMonth, 6));
    }

    public DashboardStats stats() {
        auth.require(Permission.VIEW_DATA);
        LocalDate today = LocalDate.now(clock);
        LocalDate monthStart = today.withDayOfMonth(1);
        return reportingDao.dashboardStats(today, warningDays, monthStart, monthStart.plusMonths(1).minusDays(1));
    }
}
