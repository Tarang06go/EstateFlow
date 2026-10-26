package com.estateflow.report.pdf;

import com.estateflow.dto.PaymentReceiptData;
import com.estateflow.exception.ReportExportException;
import com.estateflow.util.DateFormats;
import com.estateflow.util.MoneyUtil;

import java.io.IOException;
import java.nio.file.Path;

/** One-page A4 rent receipt. Amounts are shown as "Rs." because the standard PDF fonts have no rupee sign. */
public final class PdfReceiptWriter {

    private static final float[] INK = PdfTableReport.INK;
    private static final float[] MUTED = PdfTableReport.MUTED;
    private static final float[] BRAND = PdfTableReport.BRAND;
    private static final float[] WHITE = PdfTableReport.WHITE;
    private static final float[] RULE = PdfTableReport.RULE;
    private static final float[] SOFT = {0.898f, 0.945f, 0.910f};

    public void write(Path target, PaymentReceiptData d, String generatedOn, String pdfDate) throws ReportExportException {
        try {
            build(d, generatedOn, pdfDate).writeTo(target);
        } catch (IOException e) {
            throw new ReportExportException("Could not write the PDF receipt to " + target + ": " + e.getMessage(), e);
        }
    }

    public SimplePdf build(PaymentReceiptData d, String generatedOn, String pdfDate) {
        SimplePdf pdf = new SimplePdf(false, "Rent receipt " + d.receiptNumber(), pdfDate);
        float left = 48;
        float right = pdf.width() - 48;

        pdf.fillRect(0, 0, pdf.width(), 92, BRAND);
        PdfLogo.drawOnDark(pdf, left, 24, 46);
        pdf.text(left + 60, 46, "EstateFlow", 24, true, WHITE);
        pdf.text(left + 60, 68, "Property & Rental Management", 10, false, WHITE);
        pdf.textRight(right, 46, "RENT RECEIPT", 18, true, WHITE);
        pdf.textRight(right, 68, d.receiptNumber(), 11, false, WHITE);

        float y = 128;
        pdf.text(left, y, "Receipt date", 9, false, MUTED);
        pdf.text(left, y + 15, DateFormats.format(d.paymentDate()), 12, true, INK);
        pdf.text(left + 200, y, "Payment method", 9, false, MUTED);
        pdf.text(left + 200, y + 15, d.method().getDisplayLabel(), 12, true, INK);
        pdf.text(left + 360, y, "Reference", 9, false, MUTED);
        pdf.text(left + 360, y + 15, d.referenceNo() == null || d.referenceNo().isBlank() ? "-" : d.referenceNo(), 12, true, INK);

        y = 190;
        y = row(pdf, left, right, y, "Received from", d.tenantName());
        y = row(pdf, left, right, y, "Property", d.propertyName());
        y = row(pdf, left, right, y, "Rent period", DateFormats.format(d.periodStart()) + " to " + DateFormats.format(d.periodEnd()));

        y += 18;
        pdf.fillRect(left, y, right - left, 84, SOFT);
        pdf.strokeRect(left, y, right - left, 84, 0.75f, RULE);
        pdf.text(left + 18, y + 26, "Amount received", 10, false, MUTED);
        pdf.text(left + 18, y + 62, money(d.amount()), 28, true, INK);
        pdf.textRight(right - 18, y + 26, "Balance of this rent period", 10, false, MUTED);
        pdf.textRight(right - 18, y + 62, money(d.currentBalance()), 18, true,
                d.currentBalance().signum() == 0 ? new float[]{0.12f, 0.55f, 0.25f} : new float[]{0.70f, 0.38f, 0f});
        pdf.text(left + 18, y + 78, d.currentBalance().signum() == 0 ? "" : "", 8, false, MUTED);

        y += 84 + 18;
        pdf.text(left, y, "Balance is the amount still unpaid for this rent period at the time this receipt was generated.", 8.5f, false, MUTED);

        float footer = pdf.height() - 70;
        pdf.line(left, footer, right, footer, 0.5f, RULE);
        pdf.text(left, footer + 18, "This is a computer-generated receipt and does not require a signature.", 9, false, MUTED);
        pdf.text(left, footer + 33, "Generated " + generatedOn, 8, false, MUTED);
        pdf.textRight(right, footer + 18, "Thank you", 11, true, BRAND);
        return pdf;
    }

    private static float row(SimplePdf pdf, float left, float right, float y, String label, String value) {
        pdf.text(left, y, label, 9.5f, false, MUTED);
        pdf.text(left + 130, y, SimplePdf.fit(value, right - left - 130, 12, true), 12, true, INK);
        pdf.line(left, y + 10, right, y + 10, 0.4f, RULE);
        return y + 30;
    }

    static String money(java.math.BigDecimal amount) {
        return "Rs. " + MoneyUtil.format(amount);
    }
}
