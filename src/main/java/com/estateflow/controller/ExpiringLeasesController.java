package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.ExpiringLeaseRow;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

public class ExpiringLeasesController extends AbstractController {

    @FXML private TableView<ExpiringLeaseRow> table;
    @FXML private Label hintLabel;

    public ExpiringLeasesController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        hintLabel.setText("Agreements whose effective period ends within " + ctx.agreements().getWarningDays() + " days.");
        TableUtil.column(table, "Agreement", 90, ExpiringLeaseRow::agreementId);
        TableUtil.column(table, "Property", 180, ExpiringLeaseRow::propertyName);
        TableUtil.column(table, "Tenant", 170, ExpiringLeaseRow::tenantName);
        TableUtil.column(table, "Ends", 120, r -> DateFormats.format(r.effectiveEndDate()));
        TableUtil.column(table, "Days left", 90, ExpiringLeaseRow::daysRemaining);
        TableUtil.column(table, "Monthly rent", 120, r -> MoneyUtil.format(r.monthlyRent()));
        TableUtil.column(table, "Note", 140, r -> r.terminatedEarly() ? "Terminated early" : "");
        reload();
    }

    @FXML
    void reload() {
        UiTask.run(() -> ctx.agreements().findEndingSoon(), rows -> table.getItems().setAll(rows));
    }
}
