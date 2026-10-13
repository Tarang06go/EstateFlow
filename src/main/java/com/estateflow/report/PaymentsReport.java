package com.estateflow.report;

import com.estateflow.dto.PaymentReportRow;

import java.util.List;

/** Payments in a date range; voided payments are listed with status VOID. */
public class PaymentsReport implements ReportDefinition<PaymentReportRow> {
    public String fileName() {
        return "payments.csv";
    }

    public List<String> headers() {
        return List.of("Receipt No", "Payment Date", "Tenant", "Property", "Period Start", "Amount",
                "Method", "Reference", "Status", "Void Reason");
    }

    public List<String> toRow(PaymentReportRow r) {
        return List.of(r.receiptNumber(), String.valueOf(r.paymentDate()), r.tenantName(), r.propertyName(),
                String.valueOf(r.periodStart()), r.amount().toPlainString(), r.method().name(),
                nz(r.referenceNo()), r.voided() ? "VOID" : "VALID", nz(r.voidReason()));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
