package com.estateflow.util;

import com.estateflow.report.ReportFormat;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;

/** File choosers for report and receipt output; the chosen extension decides the format. */
public final class FileDialogs {

    private FileDialogs() {
    }

    /** PDF (default) or CSV. Returns null when cancelled. */
    public static File chooseReport(Window owner, String baseName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save report");
        FileChooser.ExtensionFilter pdf = new FileChooser.ExtensionFilter("PDF document (*.pdf)", "*.pdf");
        FileChooser.ExtensionFilter csv = new FileChooser.ExtensionFilter("CSV for Excel (*.csv)", "*.csv");
        chooser.getExtensionFilters().addAll(pdf, csv);
        chooser.setInitialFileName(baseName + ".pdf");
        return withExtension(chooser.showSaveDialog(owner), chooser.getSelectedExtensionFilter(), ".pdf");
    }

    /** PDF (default) or plain text. Returns null when cancelled. */
    public static File chooseReceipt(Window owner, String baseName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save receipt");
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("PDF document (*.pdf)", "*.pdf"),
                new FileChooser.ExtensionFilter("Text file (*.txt)", "*.txt"));
        chooser.setInitialFileName(baseName + ".pdf");
        return withExtension(chooser.showSaveDialog(owner), chooser.getSelectedExtensionFilter(), ".pdf");
    }

    public static ReportFormat formatOf(File file) {
        return file.getName().toLowerCase().endsWith(".pdf") ? ReportFormat.PDF : ReportFormat.CSV;
    }

    private static File withExtension(File file, FileChooser.ExtensionFilter selected, String fallback) {
        if (file == null || file.getName().contains(".")) {
            return file;
        }
        String ext = selected == null || selected.getExtensions().isEmpty() ? fallback
                : selected.getExtensions().get(0).substring(1);
        return new File(file.getParentFile(), file.getName() + ext);
    }
}
