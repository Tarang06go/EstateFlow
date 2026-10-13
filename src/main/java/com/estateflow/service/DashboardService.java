package com.estateflow.service;

import com.estateflow.dao.ReportingDao;
import com.estateflow.dto.DashboardCharts;
import com.estateflow.dto.DashboardStats;
import com.estateflow.dto.MonthlyAmount;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

public class DashboardService {

    private final ReportingDao reportingDao;
    private final Clock clock;
    private final int warningDays;

    public DashboardService(ReportingDao reportingDao, Clock clock, int warningDays) {
        this.reportingDao = reportingDao;
        this.clock = clock;
        this.warningDays = warningDays;
    }

    /** Last six calendar months of collections and maintenance cost, zero-filled for months without data. */
    public DashboardCharts charts() {
        YearMonth thisMonth = YearMonth.from(LocalDate.now(clock));
        LocalDate from = thisMonth.minusMonths(5).atDay(1);
        LocalDate to = thisMonth.atEndOfMonth();
        return new DashboardCharts(
                MonthlyAmount.fill(reportingDao.monthlyCollections(from, to), thisMonth, 6),
                MonthlyAmount.fill(reportingDao.monthlyMaintenanceCosts(from, to), thisMonth, 6));
    }

    public DashboardStats stats() {
        LocalDate today = LocalDate.now(clock);
        LocalDate monthStart = today.withDayOfMonth(1);
        return reportingDao.dashboardStats(today, warningDays, monthStart, monthStart.plusMonths(1).minusDays(1));
    }
}
