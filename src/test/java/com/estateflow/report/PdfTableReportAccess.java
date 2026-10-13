package com.estateflow.report;

import java.util.List;

final class PdfTableReportAccess {
    private PdfTableReportAccess() {
    }

    static float[] widths(List<String> headers, List<List<String>> rows, float usable) {
        try {
            var m = Class.forName("com.estateflow.report.pdf.PdfTableReport")
                    .getDeclaredMethod("columnWidths", List.class, List.class, float.class);
            m.setAccessible(true);
            return (float[]) m.invoke(null, headers, rows, usable);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
