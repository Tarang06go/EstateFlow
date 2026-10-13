package com.estateflow.report;

import com.estateflow.dto.RentReminderRow;
import com.estateflow.util.DateFormats;

import java.util.List;

public class RentRemindersReport implements ReportDefinition<RentReminderRow> {
    public String fileName() {
        return "rent-reminders.csv";
    }

    public List<String> headers() {
        return List.of("Status", "Property", "Tenant", "Phone", "Period Start", "Due Date", "Days", "Balance");
    }

    public List<String> toRow(RentReminderRow r) {
        String status = switch (r.category()) {
            case OVERDUE -> "OVERDUE";
            case DUE_TODAY -> "DUE TODAY";
            case UPCOMING -> "UPCOMING";
        };
        return List.of(status, r.propertyName(), r.tenantName(), r.tenantPhone() == null ? "" : r.tenantPhone(),
                DateFormats.format(r.periodStart()), DateFormats.format(r.dueDate()),
                String.valueOf(r.daysUntilDue()), r.balance().toPlainString());
    }
}
