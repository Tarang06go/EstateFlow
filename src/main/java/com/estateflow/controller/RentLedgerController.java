package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.AgreementListRow;
import com.estateflow.model.Payment;
import com.estateflow.model.RentCharge;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

public class RentLedgerController extends AbstractController {

    @FXML private ComboBox<AgreementListRow> agreementBox;
    @FXML private Label outstandingLabel;
    @FXML private TableView<RentCharge> chargeTable;
    @FXML private TableView<Payment> paymentTable;
    @FXML private Button payButton;
    private Long pendingSelection;

    public RentLedgerController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        LocalDate today = LocalDate.now(ctx.clock());
        agreementBox.setConverter(new javafx.util.StringConverter<>() {
            public String toString(AgreementListRow r) {
                return r == null ? "" : "#" + r.agreementId() + "  " + r.propertyName() + " - " + r.tenantName()
                        + "  (" + fmt.date(r.startDate()) + " to " + fmt.date(r.effectiveEndDate()) + ")";
            }

            public AgreementListRow fromString(String s) {
                return null;
            }
        });
        TableUtil.column(chargeTable, "Period", 200, c -> fmt.date(c.getPeriodStart()) + " - " + fmt.date(c.getPeriodEnd()));
        TableUtil.column(chargeTable, "Due", 110, c -> fmt.date(c.getDueDate()));
        TableUtil.column(chargeTable, "Amount due", 110, c -> fmt.money(c.getAmountDue()));
        TableUtil.column(chargeTable, "Paid", 110, c -> fmt.money(c.getAmountPaid()));
        TableUtil.column(chargeTable, "Balance", 110, c -> fmt.money(c.getBalance()));
        TableUtil.column(chargeTable, "Status", 130, c -> c.status(today));
        TableUtil.column(paymentTable, "Receipt", 130, Payment::receiptNumber);
        TableUtil.column(paymentTable, "Date", 110, p -> fmt.date(p.getPaymentDate()));
        TableUtil.column(paymentTable, "Amount", 110, p -> fmt.money(p.getAmount()));
        TableUtil.column(paymentTable, "Method", 120, p -> p.getPaymentMethod().getDisplayLabel());
        TableUtil.column(paymentTable, "Reference", 120, Payment::getReferenceNo);
        TableUtil.column(paymentTable, "Status", 90, p -> p.isVoid() ? "VOID" : "Valid");
        TableUtil.column(paymentTable, "Void reason", 220, Payment::getVoidReason);
        paymentTable.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(Payment p, boolean empty) {
                super.updateItem(p, empty);
                setStyle(!empty && p != null && p.isVoid() ? "-fx-opacity: 0.5; -fx-text-fill: grey;" : "");
            }
        });
        chargeTable.getSelectionModel().selectedItemProperty().addListener((o, a, c) -> loadPayments(c));
        agreementBox.valueProperty().addListener((o, a, r) -> loadCharges());
        UiTask.run(() -> ctx.agreements().findAllRows(), rows -> {
            agreementBox.getItems().setAll(rows);
            if (pendingSelection != null) {
                rows.stream().filter(r -> r.agreementId() == pendingSelection).findFirst().ifPresent(agreementBox::setValue);
            }
        });
    }

    /** Called by navigation from the agreement screens. */
    public void select(long agreementId) {
        this.pendingSelection = agreementId;
        agreementBox.getItems().stream().filter(r -> r.agreementId() == agreementId).findFirst().ifPresent(agreementBox::setValue);
    }

    private void loadCharges() {
        AgreementListRow row = agreementBox.getValue();
        if (row == null) {
            return;
        }
        UiTask.run(() -> new Object[]{ctx.payments().findCharges(row.agreementId()),
                ctx.payments().outstandingBalance(row.agreementId())}, data -> {
            @SuppressWarnings("unchecked") List<RentCharge> charges = (List<RentCharge>) data[0];
            Long keep = chargeTable.getSelectionModel().getSelectedItem() == null ? null
                    : chargeTable.getSelectionModel().getSelectedItem().getChargeId();
            chargeTable.getItems().setAll(charges);
            outstandingLabel.setText("Outstanding: " + fmt.money((java.math.BigDecimal) data[1]));
            if (keep != null) {
                charges.stream().filter(c -> c.getChargeId().equals(keep)).findFirst()
                        .ifPresent(c -> chargeTable.getSelectionModel().select(c));
            }
        });
    }

    private void loadPayments(RentCharge charge) {
        if (charge == null) {
            paymentTable.getItems().clear();
            return;
        }
        UiTask.run(() -> ctx.payments().findPayments(charge.getChargeId()), rows -> paymentTable.getItems().setAll(rows));
    }

    @FXML
    private void onPay() {
        RentCharge charge = chargeTable.getSelectionModel().getSelectedItem();
        if (charge == null) {
            AlertUtil.info("Record payment", "Select a rent charge first.");
            return;
        }
        nav.<PaymentFormController>showDialog("payment-form.fxml", "Record payment", c -> c.init(charge, this::loadCharges));
    }

    @FXML
    private void onVoid() {
        Payment payment = paymentTable.getSelectionModel().getSelectedItem();
        if (payment == null || payment.isVoid()) {
            AlertUtil.info("Void payment", "Select a valid (not voided) payment first.");
            return;
        }
        nav.<VoidPaymentController>showDialog("void-payment.fxml", "Void payment " + payment.receiptNumber(),
                c -> c.init(payment, () -> {
                    loadCharges();
                    loadPayments(chargeTable.getSelectionModel().getSelectedItem());
                }));
    }

    @FXML
    private void onReceipt() {
        Payment payment = paymentTable.getSelectionModel().getSelectedItem();
        if (payment == null) {
            AlertUtil.info("Receipt", "Select a payment first.");
            return;
        }
        File file = FileDialogs.chooseReceipt(nav.primaryStage(), payment.receiptNumber());
        if (file != null) {
            UiTask.runVoid(() -> ctx.receipts().writeReceipt(payment.getPaymentId(), file.toPath()),
                    () -> AlertUtil.info("Receipt saved", file.getAbsolutePath()));
        }
    }
}
