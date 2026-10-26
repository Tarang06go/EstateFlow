package com.estateflow.report;

import com.estateflow.exception.ReportExportException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** RFC 4180 CSV output, UTF-8 with a BOM so Excel detects the encoding. */
public class CsvWriter {

    private static final String BOM = "\uFEFF";
    private static final String EOL = "\r\n";

    public <T> void write(Path target, ReportDefinition<T> definition, List<T> items) throws ReportExportException {
        try (BufferedWriter out = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            out.write(BOM);
            out.write(toLine(definition.headers()));
            for (T item : items) {
                out.write(toLine(definition.toRow(item)));
            }
        } catch (IOException e) {
            throw new ReportExportException("Could not write the report to " + target + ": " + e.getMessage(), e);
        }
    }

    static String toLine(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(sanitizeSpreadsheetFormula(cells.get(i))));
        }
        return sb.append(EOL).toString();
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    /** Prevent exported user-controlled text from becoming a spreadsheet formula when opened in Excel. */
    static String sanitizeSpreadsheetFormula(String value) {
        if (value == null || value.isEmpty()) return value;
        String trimmed = value.stripLeading();
        if (trimmed.isEmpty()) return value;
        // Keep a legitimate negative decimal numeric. Other leading formula markers are treated as text.
        if (value.matches("-\\d+(\\.\\d+)?")) return value;
        char first = trimmed.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') return "'" + value;
        return value;
    }
}
