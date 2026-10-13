package com.estateflow.report;

import com.estateflow.dto.PaymentReceiptData;
import com.estateflow.exception.ReportExportException;
import com.estateflow.util.MoneyUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Formatted plain-text payment receipt. */
public class ReceiptWriter {

    public String format(PaymentReceiptData d) {
        return String.join(System.lineSeparator(),
                "ESTATEFLOW - RENT RECEIPT",
                "=========================",
                "Receipt No   : " + d.receiptNumber(),
                "Date         : " + d.paymentDate(),
                "Received from: " + d.tenantName(),
                "Property     : " + d.propertyName(),
                "Rent period  : " + d.periodStart() + " to " + d.periodEnd(),
                "Amount paid  : " + MoneyUtil.format(d.amount()),
                "Method       : " + d.method().getDisplayLabel(),
                "Reference    : " + (d.referenceNo() == null ? "-" : d.referenceNo()),
                "Balance of this rent period at time of printing: " + MoneyUtil.format(d.currentBalance()),
                "") + System.lineSeparator();
    }

    public void write(Path target, PaymentReceiptData data) throws ReportExportException {
        try {
            Files.writeString(target, format(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ReportExportException("Could not write the receipt to " + target + ": " + e.getMessage(), e);
        }
    }
}
