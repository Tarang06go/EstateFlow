package com.estateflow.service;

import com.estateflow.security.Authorizer;
import com.estateflow.security.Permission;
import com.estateflow.dao.ReportingDao;
import com.estateflow.dto.RentReminderRow;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * In-application rent reminders: unpaid, live rent charges that are overdue, due today, or due within the
 * reminder window. Cancelled and fully paid charges never appear, and each charge appears exactly once.
 */
public class RentReminderService {

    private final ReportingDao reportingDao;
    private final Clock clock;
    private final int windowDays;

    private final Authorizer auth;

    public RentReminderService(ReportingDao reportingDao, Clock clock, int windowDays,
            Authorizer auth) {
        this.auth = auth;
        this.reportingDao = reportingDao;
        this.clock = clock;
        this.windowDays = windowDays;
    }

    /** Overdue first (oldest due date first), then due today, then upcoming. */
    public List<RentReminderRow> reminders() {
        auth.require(Permission.VIEW_DATA);
        return reportingDao.findRentReminders(LocalDate.now(clock), windowDays);
    }

    public int getWindowDays() {
        return windowDays;
    }
}
