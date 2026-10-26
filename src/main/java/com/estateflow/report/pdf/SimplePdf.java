package com.estateflow.report.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

/**
 * Minimal PDF 1.4 writer: pages with text (standard Helvetica / Helvetica-Bold, WinAnsi), lines and
 * rectangles. It exists because the report layouts need nothing more, and it keeps the project free of
 * extra runtime dependencies. Coordinates are in points with the origin at the top-left corner.
 */
public final class SimplePdf {

    public static final float A4_WIDTH = 595.28f;
    public static final float A4_HEIGHT = 841.89f;

    private static final Charset WIN_ANSI = Charset.forName("windows-1252");

    private final float pageWidth;
    private final float pageHeight;
    private final String title;
    private final String creationDate;
    private final List<StringBuilder> pages = new ArrayList<>();
    private StringBuilder current;

    /** @param creationDate PDF date such as D:20261010083000Z, or null to omit */
    public SimplePdf(boolean landscape, String title, String creationDate) {
        this.pageWidth = landscape ? A4_HEIGHT : A4_WIDTH;
        this.pageHeight = landscape ? A4_WIDTH : A4_HEIGHT;
        this.title = title;
        this.creationDate = creationDate;
        newPage();
    }

    public float width() {
        return pageWidth;
    }

    public float height() {
        return pageHeight;
    }

    public int pageCount() {
        return pages.size();
    }

    public void newPage() {
        current = new StringBuilder();
        pages.add(current);
    }

    /** Switches drawing to an existing page (0-based), used to add "Page n of m" footers afterwards. */
    public void selectPage(int index) {
        current = pages.get(index);
    }

    public void text(float x, float yTop, String value, float size, boolean bold, float[] rgb) {
        current.append("BT ").append(color(rgb, false)).append('/').append(bold ? "F2" : "F1").append(' ')
                .append(num(size)).append(" Tf ").append(num(x)).append(' ').append(num(pageHeight - yTop))
                .append(" Td (").append(escape(value)).append(") Tj ET\n");
    }

    public void textRight(float xRight, float yTop, String value, float size, boolean bold, float[] rgb) {
        text(xRight - textWidth(value, size, bold), yTop, value, size, bold, rgb);
    }

    public void fillRect(float x, float yTop, float w, float h, float[] rgb) {
        current.append(color(rgb, false)).append(num(x)).append(' ').append(num(pageHeight - yTop - h)).append(' ')
                .append(num(w)).append(' ').append(num(h)).append(" re f\n");
    }

    /** Filled polygon from top-left based coordinates. */
    public void fillPolygon(float[] xs, float[] yTops, float[] rgb) {
        StringBuilder sb = new StringBuilder(color(rgb, false));
        for (int i = 0; i < xs.length; i++) {
            sb.append(num(xs[i])).append(' ').append(num(pageHeight - yTops[i])).append(i == 0 ? " m " : " l ");
        }
        current.append(sb).append("h f\n");
    }

    public void strokeRect(float x, float yTop, float w, float h, float lineWidth, float[] rgb) {
        current.append(color(rgb, true)).append(num(lineWidth)).append(" w ").append(num(x)).append(' ')
                .append(num(pageHeight - yTop - h)).append(' ').append(num(w)).append(' ').append(num(h)).append(" re S\n");
    }

    public void line(float x1, float yTop1, float x2, float yTop2, float lineWidth, float[] rgb) {
        current.append(color(rgb, true)).append(num(lineWidth)).append(" w ").append(num(x1)).append(' ')
                .append(num(pageHeight - yTop1)).append(" m ").append(num(x2)).append(' ').append(num(pageHeight - yTop2))
                .append(" l S\n");
    }

    // ---- text metrics (Helvetica AFM widths, 1/1000 em, for ASCII 32..126)

    private static final int[] WIDTHS = {
            278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
            556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
            1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
            667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
            333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
            556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584};

    public static float textWidth(String value, float size, boolean bold) {
        float total = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            total += c >= 32 && c <= 126 ? WIDTHS[c - 32] : 556;
        }
        return total * size / 1000f * (bold ? 1.06f : 1f);
    }

    /** Shortens text with "..." so it fits the width. */
    public static String fit(String value, float maxWidth, float size, boolean bold) {
        if (value == null) {
            return "";
        }
        if (textWidth(value, size, bold) <= maxWidth) {
            return value;
        }
        String s = value;
        while (!s.isEmpty() && textWidth(s + "...", size, bold) > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "...";
    }

    // ---- serialization

    public void writeTo(Path target) throws IOException {
        Files.write(target, toBytes());
    }

    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        out.write("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n".getBytes(StandardCharsets.ISO_8859_1));
        int pageCountValue = pages.size();
        // 1 catalog, 2 pages, 3 F1, 4 F2, 5 info; page i: object 6+2i, content 7+2i
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCountValue; i++) {
            kids.append(6 + 2 * i).append(" 0 R ");
        }
        object(out, offsets, "<< /Type /Catalog /Pages 2 0 R >>");
        object(out, offsets, "<< /Type /Pages /Kids [" + kids + "] /Count " + pageCountValue + " >>");
        object(out, offsets, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        object(out, offsets, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        object(out, offsets, "<< /Title (" + escape(title) + ") /Producer (EstateFlow)"
                + (creationDate == null ? "" : " /CreationDate (" + creationDate + ")") + " >>");
        for (int i = 0; i < pageCountValue; i++) {
            object(out, offsets, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + num(pageWidth) + " " + num(pageHeight)
                    + "] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents " + (7 + 2 * i) + " 0 R >>");
            byte[] content = pages.get(i).toString().getBytes(StandardCharsets.ISO_8859_1);
            ByteArrayOutputStream packed = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(packed)) {
                deflater.write(content);
            }
            offsets.add(out.size());
            out.write(((7 + 2 * i) + " 0 obj\n<< /Length " + packed.size() + " /Filter /FlateDecode >>\nstream\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.write(packed.toByteArray());
            out.write("\nendstream\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        int xref = out.size();
        StringBuilder x = new StringBuilder("xref\n0 " + (offsets.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            x.append(String.format("%010d 00000 n \n", offset));
        }
        x.append("trailer\n<< /Size ").append(offsets.size() + 1).append(" /Root 1 0 R /Info 5 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        out.write(x.toString().getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    private static void object(ByteArrayOutputStream out, List<Integer> offsets, String body) throws IOException {
        offsets.add(out.size());
        out.write(((offsets.size()) + " 0 obj\n" + body + "\nendobj\n").getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String color(float[] rgb, boolean stroke) {
        return num(rgb[0]) + " " + num(rgb[1]) + " " + num(rgb[2]) + (stroke ? " RG " : " rg ");
    }

    private static String num(float v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    /** Text as raw WinAnsi bytes (one char per byte), escaped for a PDF literal string. */
    static String escape(String value) {
        String bytesAsChars = new String((value == null ? "" : value).getBytes(WIN_ANSI), StandardCharsets.ISO_8859_1);
        StringBuilder sb = new StringBuilder();
        for (char c : bytesAsChars.toCharArray()) {
            if (c == '\\' || c == '(' || c == ')') {
                sb.append('\\');
            }
            if (c == '\r' || c == '\n') {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
