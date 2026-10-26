package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.OverdueRentRow;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

import java.io.File;
import java.math.BigDecimal;

public class OverdueRentController extends AbstractController {

    @FXML private TableView<OverdueRentRow> table;
    @FXML private Label totalLabel;

    public OverdueRentController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        TableUtil.column(table, "Property", 160, OverdueRentRow::propertyName);
        TableUtil.column(table, "Tenant", 150, OverdueRentRow::tenantName);
        TableUtil.column(table, "Phone", 110, OverdueRentRow::tenantPhone);
        TableUtil.column(table, "Period start", 110, r -> fmt.date(r.periodStart()));
        TableUtil.column(table, "Due date", 110, r -> fmt.date(r.dueDate()));
        TableUtil.column(table, "Amount due", 100, r -> fmt.money(r.amountDue()));
        TableUtil.column(table, "Paid", 100, r -> fmt.money(r.amountPaid()));
        TableUtil.column(table, "Balance", 100, r -> fmt.money(r.balance()));
        TableUtil.column(table, "Days overdue", 100, OverdueRentRow::daysOverdue);
        reload();
    }

    @FXML
    void reload() {
        UiTask.run(() -> ctx.payments().findOverdue(), rows -> {
            table.getItems().setAll(rows);
            BigDecimal total = rows.stream().map(OverdueRentRow::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
            totalLabel.setText(rows.size() + " overdue charge(s), total " + fmt.money(total));
        });
    }

    @FXML
    private void onLedger() {
        OverdueRentRow row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            AlertUtil.info("Overdue rent", "Select a row first.");
            return;
        }
        RentLedgerController ledger = nav.show("ledger.fxml");
        ledger.select(row.agreementId());
    }

    @FXML
    private void onExport() {
        File file = FileDialogs.chooseReport(nav.primaryStage(), "overdue-rent");
        if (file != null) {
            UiTask.run(() -> ctx.reports().exportOverdue(file.toPath(), FileDialogs.formatOf(file)),
                    n -> AlertUtil.info("Export complete", n + " row(s) written to " + file.getAbsolutePath()));
        }
    }
}
