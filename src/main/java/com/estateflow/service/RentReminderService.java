package com.estateflow.service;

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

    public RentReminderService(ReportingDao reportingDao, Clock clock, int windowDays) {
        this.reportingDao = reportingDao;
        this.clock = clock;
        this.windowDays = windowDays;
    }

    /** Overdue first (oldest due date first), then due today, then upcoming. */
    public List<RentReminderRow> reminders() {
        return reportingDao.findRentReminders(LocalDate.now(clock), windowDays);
    }

    public int getWindowDays() {
        return windowDays;
    }
}
