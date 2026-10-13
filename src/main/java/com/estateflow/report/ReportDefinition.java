package com.estateflow.report;

import java.util.List;

/** Describes one CSV report: file name, header row, and how a row item becomes cells. */
public interface ReportDefinition<T> {
    String fileName();

    List<String> headers();

    List<String> toRow(T item);
}
