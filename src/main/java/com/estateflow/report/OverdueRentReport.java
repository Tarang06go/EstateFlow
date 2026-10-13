package com.estateflow.report;

import com.estateflow.dto.OverdueRentRow;

import java.util.List;

public class OverdueRentReport implements ReportDefinition<OverdueRentRow> {
    public String fileName() {
        return "overdue-rent.csv";
    }

    public List<String> headers() {
        return List.of("Property", "Tenant", "Phone", "Period Start", "Due Date", "Amount Due", "Amount Paid",
                "Balance", "Days Overdue");
    }

    public List<String> toRow(OverdueRentRow r) {
        return List.of(r.propertyName(), r.tenantName(), r.tenantPhone() == null ? "" : r.tenantPhone(),
                String.valueOf(r.periodStart()), String.valueOf(r.dueDate()), r.amountDue().toPlainString(),
                r.amountPaid().toPlainString(), r.balance().toPlainString(), String.valueOf(r.daysOverdue()));
    }
}
