package com.estateflow.report;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CsvWriterSecurityTest {
    @Test void dangerousTextIsNeutralizedButNegativeAmountsStayNumeric() {
        assertEquals("'=1+1", CsvWriter.sanitizeSpreadsheetFormula("=1+1"));
        assertEquals("'  @SUM(A1:A2)", CsvWriter.sanitizeSpreadsheetFormula("  @SUM(A1:A2)"));
        assertEquals("'-cmd", CsvWriter.sanitizeSpreadsheetFormula("-cmd"));
        assertEquals("-12.50", CsvWriter.sanitizeSpreadsheetFormula("-12.50"));
        assertEquals("42.00", CsvWriter.sanitizeSpreadsheetFormula("42.00"));
    }

    @Test void csvWriterProtectsValuesBeforeQuoting() {
        ReportDefinition<String> row = new ReportDefinition<>() {
            public String fileName() { return "test"; }
            public List<String> headers() { return List.of("Value"); }
            public List<String> toRow(String value) { return List.of(value); }
        };
        String line = CsvWriter.toLine(row.toRow("=HYPERLINK(\"https://bad\")"));
        assertTrue(line.startsWith("\"'=HYPERLINK"));
        assertTrue(line.endsWith("\r\n"));
    }
}
