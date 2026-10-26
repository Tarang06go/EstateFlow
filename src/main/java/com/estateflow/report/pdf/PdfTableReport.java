package com.estateflow.report.pdf;

import com.estateflow.exception.ReportExportException;
import com.estateflow.report.ReportDefinition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Renders any {@link ReportDefinition} as a paginated landscape A4 table with a header and page footers. */
public final class PdfTableReport {

    static final float[] INK = {0.110f, 0.153f, 0.133f};
    static final float[] MUTED = {0.400f, 0.443f, 0.416f};
    static final float[] BRAND = {0.141f, 0.357f, 0.271f};
    static final float[] ZEBRA = {0.965f, 0.969f, 0.957f};
    static final float[] WHITE = {1f, 1f, 1f};
    static final float[] RULE = {0.890f, 0.910f, 0.890f};

    private static final float MARGIN = 36f;
    private static final float ROW_HEIGHT = 17f;
    private static final float FONT = 8.5f;
    private static final float FIRST_PAGE_TABLE_TOP = 92f;
    private static final float NEXT_PAGE_TABLE_TOP = 54f;
    private static final float BOTTOM_RESERVED = 44f;

    private PdfTableReport() {
    }

    public static <T> void write(Path target, String title, String subtitle, List<String> summaryLines,
                                 ReportDefinition<T> definition, List<T> items, String generatedOn, String pdfDate)
            throws ReportExportException {
        try {
            build(title, subtitle, summaryLines, definition, items, generatedOn, pdfDate).writeTo(target);
        } catch (IOException e) {
            throw new ReportExportException("Could not write the PDF report to " + target + ": " + e.getMessage(), e);
        }
    }

    /** Builds the document in memory (exposed for tests). */
    public static <T> SimplePdf build(String title, String subtitle, List<String> summaryLines,
                                      ReportDefinition<T> definition, List<T> items, String generatedOn, String pdfDate) {
        SimplePdf pdf = new SimplePdf(true, title, pdfDate);
        float usable = pdf.width() - 2 * MARGIN;
        List<String> headers = definition.headers();
        List<List<String>> rows = new ArrayList<>();
        for (T item : items) {
            rows.add(definition.toRow(item));
        }
        float[] widths = columnWidths(headers, rows, usable);

        int firstCapacity = capacity(pdf.height(), FIRST_PAGE_TABLE_TOP, summaryLines.size());
        int nextCapacity = capacity(pdf.height(), NEXT_PAGE_TABLE_TOP, 0);
        int remaining = Math.max(0, Math.max(rows.size(), 1) - firstCapacity);
        int pageTotal = 1 + (int) Math.ceil(remaining / (double) nextCapacity);

        int index = 0;
        for (int page = 0; page < pageTotal; page++) {
            if (page > 0) {
                pdf.newPage();
            }
            float top;
            if (page == 0) {
                drawFirstHeader(pdf, title, subtitle);
                top = FIRST_PAGE_TABLE_TOP;
            } else {
                pdf.text(MARGIN, 34, title + " (continued)", 10, true, INK);
                top = NEXT_PAGE_TABLE_TOP;
            }
            top = drawTableHeader(pdf, headers, widths, top);
            int capacity = page == 0 ? firstCapacity : nextCapacity;
            if (rows.isEmpty()) {
                pdf.text(MARGIN + 6, top + 12, "No records match this report.", 10, false, MUTED);
            }
            for (int r = 0; r < capacity && index < rows.size(); r++, index++) {
                drawRow(pdf, rows.get(index), widths, top + r * ROW_HEIGHT, r % 2 == 1);
            }
            if (page == pageTotal - 1) {
                float y = top + Math.max(1, Math.min(capacity, rows.size() - (index - Math.min(capacity, index)))) * ROW_HEIGHT;
                y = Math.min(y, pdf.height() - BOTTOM_RESERVED - 6);
                for (String line : summaryLines) {
                    y += 14;
                    pdf.text(MARGIN, Math.min(y, pdf.height() - BOTTOM_RESERVED + 6), line, 9.5f, true, INK);
                }
            }
        }
        for (int p = 0; p < pageTotal; p++) {
            pdf.selectPage(p);
            pdf.line(MARGIN, pdf.height() - 32, pdf.width() - MARGIN, pdf.height() - 32, 0.5f, RULE);
            pdf.text(MARGIN, pdf.height() - 20, "EstateFlow | Generated " + generatedOn, 8, false, MUTED);
            pdf.textRight(pdf.width() - MARGIN, pdf.height() - 20, "Page " + (p + 1) + " of " + pageTotal, 8, false, MUTED);
        }
        return pdf;
    }

    private static int capacity(float pageHeight, float tableTop, int summaryLines) {
        float space = pageHeight - BOTTOM_RESERVED - tableTop - ROW_HEIGHT - summaryLines * 14f;
        return Math.max(1, (int) (space / ROW_HEIGHT));
    }

    private static void drawFirstHeader(SimplePdf pdf, String title, String subtitle) {
        pdf.fillRect(0, 0, pdf.width(), 58, BRAND);
        PdfLogo.drawOnDark(pdf, MARGIN, 13, 32);
        pdf.text(MARGIN + 44, 35, title, 17, true, WHITE);
        pdf.textRight(pdf.width() - MARGIN, 35, "EstateFlow", 13, true, WHITE);
        pdf.text(MARGIN, 76, subtitle == null ? "" : subtitle, 9.5f, false, MUTED);
    }

    private static float drawTableHeader(SimplePdf pdf, List<String> headers, float[] widths, float top) {
        pdf.fillRect(MARGIN, top, sum(widths), ROW_HEIGHT, INK);
        float x = MARGIN;
        for (int c = 0; c < headers.size(); c++) {
            pdf.text(x + 4, top + 11.5f, SimplePdf.fit(headers.get(c), widths[c] - 8, FONT, true), FONT, true, WHITE);
            x += widths[c];
        }
        return top + ROW_HEIGHT;
    }

    private static void drawRow(SimplePdf pdf, List<String> row, float[] widths, float top, boolean zebra) {
        if (zebra) {
            pdf.fillRect(MARGIN, top, sum(widths), ROW_HEIGHT, ZEBRA);
        }
        float x = MARGIN;
        for (int c = 0; c < widths.length; c++) {
            String cell = c < row.size() && row.get(c) != null ? row.get(c) : "";
            String shown = SimplePdf.fit(cell, widths[c] - 8, FONT, false);
            if (isNumeric(cell)) {
                pdf.textRight(x + widths[c] - 4, top + 11.5f, shown, FONT, false, INK);
            } else {
                pdf.text(x + 4, top + 11.5f, shown, FONT, false, INK);
            }
            x += widths[c];
        }
        pdf.line(MARGIN, top + ROW_HEIGHT, MARGIN + sum(widths), top + ROW_HEIGHT, 0.25f, RULE);
    }

    static boolean isNumeric(String s) {
        return s != null && s.matches("-?[\\d,]+(\\.\\d+)?");
    }

    /** Widths proportional to content (header or longest cell, capped), scaled to fill the usable width. */
    static float[] columnWidths(List<String> headers, List<List<String>> rows, float usable) {
        float[] want = new float[headers.size()];
        float cap = usable * 0.30f;
        for (int c = 0; c < want.length; c++) {
            float w = SimplePdf.textWidth(headers.get(c), FONT, true);
            for (List<String> row : rows) {
                if (c < row.size() && row.get(c) != null) {
                    w = Math.max(w, SimplePdf.textWidth(row.get(c), FONT, false));
                }
            }
            want[c] = Math.min(w, cap) + 10;
        }
        float total = sum(want);
        float[] out = new float[want.length];
        for (int c = 0; c < want.length; c++) {
            out[c] = want[c] * usable / total;
        }
        return out;
    }

    private static float sum(float[] values) {
        float s = 0;
        for (float v : values) {
            s += v;
        }
        return s;
    }
}
