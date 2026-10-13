package com.estateflow.report;

import com.estateflow.dto.PaymentReceiptData;
import com.estateflow.dto.PaymentReportRow;
import com.estateflow.model.enums.PaymentMethod;
import com.estateflow.report.pdf.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.*;

class PdfReportsTest {

    /** Inflates every content stream and returns all drawing operators as one text. */
    static String contentText(byte[] pdf) throws Exception {
        String raw = new String(pdf, StandardCharsets.ISO_8859_1);
        StringBuilder all = new StringBuilder();
        Matcher m = Pattern.compile("(?<!end)stream\n").matcher(raw);
        while (m.find()) {
            int start = m.end();
            int end = raw.indexOf("\nendstream", start);
            byte[] packed = raw.substring(start, end).getBytes(StandardCharsets.ISO_8859_1);
            all.append(new String(new InflaterInputStream(new ByteArrayInputStream(packed)).readAllBytes(),
                    StandardCharsets.ISO_8859_1)).append('\f');
        }
        return all.toString();
    }

    static PaymentReceiptData receipt(String balance) {
        return new PaymentReceiptData("RCP-2026-000012", LocalDate.of(2026, 10, 10), "Ravi (Kumar)", "Flat 4B",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), new BigDecimal("5000.00"), PaymentMethod.UPI,
                "UPI-77", new BigDecimal(balance));
    }

    @Test
    @DisplayName("PDF structure: header, xref offsets point at their objects, trailer and EOF are present")
    void structure_xrefOffsetsAreExact() throws Exception {
        byte[] bytes = new PdfReceiptWriter().build(receipt("0.00"), "10 Oct 2026 08:00", "D:20261010080000Z").toBytes();
        String raw = new String(bytes, StandardCharsets.ISO_8859_1);
        assertTrue(raw.startsWith("%PDF-1.4"));
        assertTrue(raw.trim().endsWith("%%EOF"));
        int xref = Integer.parseInt(raw.substring(raw.lastIndexOf("startxref") + 10).trim().split("\\s")[0]);
        assertTrue(raw.startsWith("xref", xref));
        String[] lines = raw.substring(xref).split("\n");
        int objects = Integer.parseInt(lines[1].split(" ")[1]);
        for (int i = 1; i < objects; i++) {
            int offset = Integer.parseInt(lines[2 + i].substring(0, 10));
            assertTrue(raw.startsWith(i + " 0 obj", offset), "object " + i + " not at its xref offset");
        }
    }

    @Test
    @DisplayName("Receipt PDF shows number, tenant, property, period, amount, method, reference and balance")
    void receipt_containsAllDetails() throws Exception {
        String text = contentText(new PdfReceiptWriter().build(receipt("1250.50"), "10 Oct 2026 08:00", null).toBytes());
        for (String expected : List.of("RCP-2026-000012", "Ravi \\(Kumar\\)", "Flat 4B", "01 Oct 2026 to 31 Oct 2026",
                "Rs. 5,000.00", "UPI-77", "Rs. 1,250.50", "10 Oct 2026", "UPI")) {
            assertTrue(text.contains(expected), "missing: " + expected);
        }
    }

    @Test
    void receipt_writesFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("r.pdf");
        new PdfReceiptWriter().write(file, receipt("0.00"), "10 Oct 2026 08:00", "D:20261010080000Z");
        assertTrue(Files.size(file) > 800);
    }

    @Test
    @DisplayName("Text escaping: parentheses and backslashes are escaped; characters outside WinAnsi become '?'")
    void escape_specialCharacters() {
        assertEquals("a\\(b\\)\\\\c", SimplePdfAccess.escape("a(b)\\c"));
        assertEquals("x ?", SimplePdfAccess.escape("x \u0928"));
        assertEquals("caf\u00e9", new String(SimplePdfAccess.escape("caf\u00e9").getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.ISO_8859_1));
    }

    @Test
    void textMetrics_fitTruncatesWithEllipsis() {
        assertEquals("short", SimplePdf.fit("short", 200, 10, false));
        String cut = SimplePdf.fit("A very long property name that cannot fit", 60, 10, false);
        assertTrue(cut.endsWith("...") && SimplePdf.textWidth(cut, 10, false) <= 60 + 0.01);
        assertTrue(SimplePdf.textWidth("MMM", 10, false) > SimplePdf.textWidth("iii", 10, false));
        assertEquals("", SimplePdf.fit(null, 50, 10, false));
    }

    private static List<PaymentReportRow> rows(int n) {
        List<PaymentReportRow> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            list.add(new PaymentReportRow(i, "RCP-2026-" + String.format("%06d", i), LocalDate.of(2026, 10, 1).plusDays(i % 9),
                    "Tenant " + i, "Property " + i, LocalDate.of(2026, 10, 1), new BigDecimal("1000.00").add(BigDecimal.valueOf(i)),
                    PaymentMethod.CASH, null, i % 10 == 0, i % 10 == 0 ? "typo" : null));
        }
        return list;
    }

    @Test
    @DisplayName("Table report paginates: every row appears once, header repeats, footers say 'Page n of m'")
    void tableReport_paginatesAndNumbersPages() throws Exception {
        SimplePdf pdf = PdfTableReport.build("Payments report", "From 01 Oct 2026", List.of("Total: Rs. 1.00"),
                new PaymentsReport(), rows(120), "10 Oct 2026 08:00", null);
        assertTrue(pdf.pageCount() >= 3, "pages: " + pdf.pageCount());
        String text = contentText(pdf.toBytes());
        String[] pages = text.split("\f");
        assertEquals(pdf.pageCount(), pages.length);
        for (int i = 0; i < pages.length; i++) {
            assertTrue(pages[i].contains("(Page " + (i + 1) + " of " + pdf.pageCount() + ")"));
            assertTrue(pages[i].contains("(Receipt No)"), "header missing on page " + (i + 1));
        }
        for (int i = 1; i <= 120; i++) {
            String id = "RCP-2026-" + String.format("%06d", i);
            assertEquals(1, text.split("\\(" + id + "\\)", -1).length - 1, id + " must appear exactly once");
        }
        assertTrue(text.contains("(Total: Rs. 1.00)"));
    }

    @Test
    void tableReport_emptyShowsMessage(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("e.pdf");
        PdfTableReport.write(file, "Overdue rent", "As of today", List.of("0 overdue charge(s)"), new OverdueRentReport(),
                List.of(), "10 Oct 2026 08:00", "D:20261010080000Z");
        assertTrue(contentText(Files.readAllBytes(file)).contains("No records match this report."));
    }

    @Test
    void tableReport_unwritablePath_throwsReportExportExceptionWithCause(@TempDir Path dir) {
        var e = assertThrows(com.estateflow.exception.ReportExportException.class, () -> PdfTableReport.write(
                dir.resolve("missing/x.pdf"), "t", "s", List.of(), new OverdueRentReport(), List.of(), "now", null));
        assertNotNull(e.getCause());
    }

    @Test
    void columnWidths_fillUsableWidth() {
        float[] widths = PdfTableReportAccess.widths(List.of("A", "Long header text"), List.of(List.of("1", "x")), 700f);
        assertEquals(700f, widths[0] + widths[1], 0.01f);
    }
}
