package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.PaymentReportRow;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Searchable payment history (valid and voided) with receipt output. */
public class PaymentsController extends AbstractController {

    private static final String ALL = "All", VALID = "Valid", VOIDED = "Voided";

    @FXML private TextField searchField;
    @FXML private DatePicker fromPicker, toPicker;
    @FXML private ComboBox<String> statusBox;
    @FXML private Label countLabel;
    @FXML private TableView<PaymentReportRow> table;

    public PaymentsController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        statusBox.getItems().setAll(ALL, VALID, VOIDED);
        statusBox.setValue(ALL);
        table.setPlaceholder(new Label("No payments match these filters."));
        TableUtil.column(table, "Receipt", 130, PaymentReportRow::receiptNumber);
        TableUtil.column(table, "Date", 105, r -> DateFormats.format(r.paymentDate()));
        TableUtil.column(table, "Tenant", 160, PaymentReportRow::tenantName);
        TableUtil.column(table, "Property", 170, PaymentReportRow::propertyName);
        TableUtil.column(table, "Rent period", 105, r -> DateFormats.format(r.periodStart()));
        TableUtil.column(table, "Amount", 105, r -> MoneyUtil.format(r.amount()));
        TableUtil.column(table, "Method", 110, r -> r.method().getDisplayLabel());
        TableUtil.column(table, "Reference", 110, PaymentReportRow::referenceNo);
        TableUtil.column(table, "Status", 80, r -> r.voided() ? "VOID" : "Valid");
        TableUtil.column(table, "Void reason", 170, PaymentReportRow::voidReason);
        table.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(PaymentReportRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().remove("row-void");
                if (!empty && row != null && row.voided()) {
                    getStyleClass().add("row-void");
                }
            }
        });
        reload();
    }

    @FXML
    void reload() {
        String text = searchField.getText();
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        String status = statusBox.getValue();
        Boolean voided = VALID.equals(status) ? Boolean.FALSE : VOIDED.equals(status) ? Boolean.TRUE : null;
        UiTask.run(() -> ctx.payments().search(text, from, to, voided), rows -> {
            table.getItems().setAll(rows);
            BigDecimal valid = rows.stream().filter(r -> !r.voided()).map(PaymentReportRow::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            countLabel.setText(rows.size() + " payment(s) shown (newest first, max 500)  |  valid total " + MoneyUtil.format(valid));
        });
    }

    @FXML
    private void onClear() {
        searchField.clear();
        fromPicker.setValue(null);
        toPicker.setValue(null);
        statusBox.setValue(ALL);
        reload();
    }

    @FXML
    private void onReceipt() {
        PaymentReportRow row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            AlertUtil.info("Receipt", "Select a payment first.");
            return;
        }
        File file = FileDialogs.chooseReceipt(nav.primaryStage(), row.receiptNumber());
        if (file != null) {
            UiTask.runVoid(() -> ctx.receipts().writeReceipt(row.paymentId(), file.toPath()),
                    () -> AlertUtil.info("Receipt saved", file.getAbsolutePath()));
        }
    }
}
