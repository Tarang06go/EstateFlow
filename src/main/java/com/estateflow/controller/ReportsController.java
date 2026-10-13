package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.model.enums.MaintenanceStatus;
import com.estateflow.report.ReportFormat;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReportsController extends AbstractController {

    private static final String ALL = "All";

    @FXML private DatePicker fromPicker, toPicker;
    @FXML private ComboBox<String> statusBox;
    @FXML private ComboBox<ReportFormat> formatBox;
    @FXML private Label resultLabel;

    public ReportsController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        LocalDate today = LocalDate.now(ctx.clock());
        fromPicker.setValue(today.withDayOfMonth(1));
        toPicker.setValue(today);
        List<String> options = new ArrayList<>(List.of(ALL));
        for (MaintenanceStatus s : MaintenanceStatus.values()) {
            options.add(s.name());
        }
        statusBox.getItems().setAll(options);
        statusBox.setValue(ALL);
        formatBox.getItems().setAll(ReportFormat.values());
        formatBox.setValue(ReportFormat.PDF);
    }

    private File choose(String baseName) {
        ReportFormat format = formatBox.getValue() == null ? ReportFormat.PDF : formatBox.getValue();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save report");
        chooser.setInitialFileName(baseName + "." + format.extension());
        chooser.getExtensionFilters().add(format == ReportFormat.PDF
                ? new FileChooser.ExtensionFilter("PDF document", "*.pdf") : new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File file = chooser.showSaveDialog(nav.primaryStage());
        if (file != null && !file.getName().contains(".")) {
            file = new File(file.getParentFile(), file.getName() + "." + format.extension());
        }
        return file;
    }

    private ReportFormat format() {
        return formatBox.getValue() == null ? ReportFormat.PDF : formatBox.getValue();
    }

    private void done(File file, int rows) {
        resultLabel.setText(rows + " row(s) written to " + file.getAbsolutePath());
    }

    @FXML
    private void onPayments() {
        try {
            LocalDate from = InputParser.requiredDate("from", "From date", fromPicker.getValue());
            LocalDate to = InputParser.requiredDate("to", "To date", toPicker.getValue());
            File file = choose("payments");
            if (file != null) {
                UiTask.run(() -> ctx.reports().exportPayments(from, to, file.toPath(), format()), n -> done(file, n));
            }
        } catch (EstateFlowException e) {
            ErrorHandler.handle(e);
        }
    }

    @FXML
    private void onOverdue() {
        File file = choose("overdue-rent");
        if (file != null) {
            UiTask.run(() -> ctx.reports().exportOverdue(file.toPath(), format()), n -> done(file, n));
        }
    }

    @FXML
    private void onOccupancy() {
        File file = choose("occupancy");
        if (file != null) {
            UiTask.run(() -> ctx.reports().exportOccupancy(file.toPath(), format()), n -> done(file, n));
        }
    }

    @FXML
    private void onMaintenance() {
        String chosen = statusBox.getValue();
        MaintenanceStatus status = chosen == null || ALL.equals(chosen) ? null : MaintenanceStatus.valueOf(chosen);
        File file = choose("maintenance");
        if (file != null) {
            UiTask.run(() -> ctx.reports().exportMaintenance(status, file.toPath(), format()), n -> done(file, n));
        }
    }

    @FXML
    private void onReminders() {
        File file = choose("rent-reminders");
        if (file != null) {
            UiTask.run(() -> ctx.reports().exportRentReminders(file.toPath(), format()), n -> done(file, n));
        }
    }
}
