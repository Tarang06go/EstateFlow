package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.MaintenanceReportRow;
import com.estateflow.model.enums.MaintenanceStatus;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.ArrayList;
import java.util.List;

public class MaintenanceListController extends AbstractController {

    private static final String ALL = "All";

    @FXML private ComboBox<String> statusFilter;
    @FXML private TableView<MaintenanceReportRow> table;

    public MaintenanceListController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        List<String> options = new ArrayList<>(List.of(ALL));
        for (MaintenanceStatus s : MaintenanceStatus.values()) {
            options.add(s.name());
        }
        statusFilter.getItems().setAll(options);
        statusFilter.setValue(ALL);
        table.setPlaceholder(new Label("No maintenance requests. Use \"New request\" to add one."));
        statusFilter.valueProperty().addListener((o, a, b) -> reload());
        TableUtil.column(table, "No.", 55, MaintenanceReportRow::requestId);
        TableUtil.column(table, "Property", 150, MaintenanceReportRow::propertyName);
        TableUtil.column(table, "Title", 200, MaintenanceReportRow::title);
        TableUtil.column(table, "Priority", 80, MaintenanceReportRow::priority);
        TableUtil.column(table, "Status", 100, MaintenanceReportRow::status);
        TableUtil.column(table, "Reported", 110, r -> DateFormats.format(r.reportedDate()));
        TableUtil.column(table, "Resolved", 110, r -> DateFormats.format(r.resolvedDate()));
        TableUtil.column(table, "Assigned to", 120, MaintenanceReportRow::assignedTo);
        TableUtil.column(table, "Cost", 90, r -> r.cost() == null ? "" : MoneyUtil.format(r.cost()));
        reload();
    }

    @FXML
    void reload() {
        String chosen = statusFilter.getValue();
        MaintenanceStatus status = chosen == null || ALL.equals(chosen) ? null : MaintenanceStatus.valueOf(chosen);
        UiTask.run(() -> ctx.maintenance().findRows(status), rows -> table.getItems().setAll(rows));
    }

    @FXML
    private void onAnalytics() {
        nav.show("maintenance-analytics.fxml");
    }

    @FXML
    private void onAdd() {
        nav.<MaintenanceFormController>showDialog("maintenance-form.fxml", "New maintenance request", c -> c.init(null, this::reload));
    }

    private MaintenanceReportRow selected() {
        MaintenanceReportRow row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            AlertUtil.info("Maintenance", "Select a request first.");
        }
        return row;
    }

    @FXML
    private void onEdit() {
        MaintenanceReportRow row = selected();
        if (row != null) {
            UiTask.run(() -> ctx.maintenance().get(row.requestId()), request ->
                    nav.<MaintenanceFormController>showDialog("maintenance-form.fxml", "Edit request", c -> c.init(request, this::reload)));
        }
    }

    @FXML
    private void onChangeStatus() {
        MaintenanceReportRow row = selected();
        if (row == null) {
            return;
        }
        List<MaintenanceStatus> next = new ArrayList<>();
        for (MaintenanceStatus s : MaintenanceStatus.values()) {
            if (row.status().canTransitionTo(s)) {
                next.add(s);
            }
        }
        if (next.isEmpty()) {
            AlertUtil.info("Maintenance", "A " + row.status() + " request cannot change status.");
            return;
        }
        ChoiceDialog<MaintenanceStatus> dialog = new ChoiceDialog<>(next.get(0), next);
        dialog.setTitle("Change status");
        dialog.setHeaderText(null);
        dialog.setContentText("Move request " + row.requestId() + " to:");
        dialog.showAndWait().ifPresent(target ->
                UiTask.run(() -> ctx.maintenance().changeStatus(row.requestId(), target, null), r -> reload()));
    }
}
