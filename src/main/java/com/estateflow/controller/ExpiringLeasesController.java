package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.ExpiringLeaseRow;
import com.estateflow.dto.LeaseAttentionRow;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.ArrayList;
import java.util.List;

/** Leases ending within a chosen period (30/60/90/180 days) and ended leases that still need action. */
public class ExpiringLeasesController extends AbstractController {

    @FXML private ComboBox<Integer> periodBox;
    @FXML private TableView<ExpiringLeaseRow> table;
    @FXML private TableView<LeaseAttentionRow> attentionTable;
    @FXML private Label hintLabel;

    public ExpiringLeasesController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        List<Integer> periods = new ArrayList<>(List.of(30, 60, 90, 180));
        int configured = ctx.agreements().getWarningDays();
        if (!periods.contains(configured)) {
            periods.add(configured);
            periods.sort(null);
        }
        periodBox.getItems().setAll(periods);
        periodBox.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Integer d) { return d == null ? "" : d + " days"; }
            public Integer fromString(String s) { return null; }
        });
        periodBox.setValue(configured);
        periodBox.valueProperty().addListener((o, a, b) -> reload());

        table.setPlaceholder(new Label("No leases end in this period."));
        TableUtil.column(table, "Agreement", 90, ExpiringLeaseRow::agreementId);
        TableUtil.column(table, "Property", 180, ExpiringLeaseRow::propertyName);
        TableUtil.column(table, "Tenant", 170, ExpiringLeaseRow::tenantName);
        TableUtil.column(table, "Ends", 120, r -> fmt.date(r.effectiveEndDate()));
        TableUtil.column(table, "Days left", 90, ExpiringLeaseRow::daysRemaining);
        TableUtil.column(table, "Monthly rent", 120, r -> fmt.money(r.monthlyRent()));
        TableUtil.column(table, "Note", 140, r -> r.terminatedEarly() ? "Terminated early" : "");

        attentionTable.setPlaceholder(new Label("Nothing needs attention: every ended lease is renewed, paid up and has no deposit held."));
        TableUtil.column(attentionTable, "Agreement", 90, LeaseAttentionRow::agreementId);
        TableUtil.column(attentionTable, "Property", 170, LeaseAttentionRow::propertyName);
        TableUtil.column(attentionTable, "Tenant", 160, LeaseAttentionRow::tenantName);
        TableUtil.column(attentionTable, "Ended", 110, r -> fmt.date(r.effectiveEnd()) + (r.terminatedEarly() ? " (early)" : ""));
        TableUtil.column(attentionTable, "Days ago", 80, LeaseAttentionRow::daysSinceEnd);
        TableUtil.column(attentionTable, "Rent owing", 110, r -> fmt.money(r.rentOutstanding()));
        TableUtil.column(attentionTable, "Deposit held", 110, r -> fmt.money(r.depositBalance()));
        TableUtil.column(attentionTable, "Action needed", 230, r -> {
            List<String> todo = new ArrayList<>();
            if (r.rentOutstanding().signum() > 0) {
                todo.add("collect rent");
            }
            if (r.depositBalance().signum() > 0) {
                todo.add("settle deposit");
            }
            return String.join(" and ", todo);
        });
        reload();
    }

    @FXML
    void reload() {
        int days = periodBox.getValue() == null ? ctx.agreements().getWarningDays() : periodBox.getValue();
        hintLabel.setText("Leases whose effective period ends within " + days + " days.");
        UiTask.run(() -> ctx.leases().endingWithin(days), rows -> table.getItems().setAll(rows));
        UiTask.run(() -> ctx.leases().needingAttention(), rows -> attentionTable.getItems().setAll(rows), failure -> {
            attentionTable.setPlaceholder(new Label("This list needs the v1.5 migration (see README)."));
            attentionTable.getItems().clear();
        });
    }

    @FXML
    private void onOpenEnding() {
        ExpiringLeaseRow row = table.getSelectionModel().getSelectedItem();
        open(row == null ? null : row.agreementId());
    }

    @FXML
    private void onOpenAttention() {
        LeaseAttentionRow row = attentionTable.getSelectionModel().getSelectedItem();
        open(row == null ? null : row.agreementId());
    }

    private void open(Long agreementId) {
        if (agreementId == null) {
            AlertUtil.info("Open agreement", "Select a lease first.");
            return;
        }
        nav.<AgreementDetailController>showDialog("agreement-detail.fxml", "Agreement " + agreementId, c -> c.init(agreementId, this::reload));
    }
}
