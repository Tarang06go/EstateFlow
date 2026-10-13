package com.estateflow.report;

/** Output format of a report export. */
public enum ReportFormat {
    CSV("csv"), PDF("pdf");

    private final String extension;

    ReportFormat(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }
}
