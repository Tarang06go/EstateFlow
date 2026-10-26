package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.controller.DepositDetailController.Mode;
import com.estateflow.dto.DepositRow;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.model.enums.PaymentMethod;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Collect, deduct or refund. DepositService re-validates everything, including the balance, under a row lock. */
public class DepositFormController extends AbstractDialogController {

    @FXML private Label infoLabel, methodLabel, reasonLabel, referenceLabel;
    @FXML private TextField amountField, reasonField, referenceField;
    @FXML private DatePicker datePicker;
    @FXML private ComboBox<PaymentMethod> methodBox;
    @FXML private TextArea notesArea;
    @FXML private Button saveButton;

    private DepositRow position;
    private Mode mode = Mode.COLLECT;
    private Runnable onSaved = () -> { };

    public DepositFormController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        methodBox.getItems().setAll(PaymentMethod.values());
        methodBox.setValue(PaymentMethod.BANK_TRANSFER);
        datePicker.setValue(LocalDate.now(ctx.clock()));
    }

    void init(DepositRow position, Mode mode, Runnable onSaved) {
        this.position = position;
        this.mode = mode;
        this.onSaved = onSaved;
        BigDecimal balance = position.totals().balance();
        switch (mode) {
            case COLLECT -> {
                show(false, reasonLabel, reasonField);
                BigDecimal remaining = position.required().signum() > 0 ? position.required().subtract(position.totals().original()) : null;
                infoLabel.setText("Collect money received from " + position.tenantName() + ". "
                        + (remaining == null ? "The lease lists no deposit amount." : "Still to collect under the lease: " + fmt.money(remaining.max(BigDecimal.ZERO)) + "."));
                if (remaining != null && remaining.signum() > 0) {
                    amountField.setText(remaining.toPlainString());
                }
            }
            case DEDUCT -> {
                show(false, methodLabel, methodBox);
                referenceLabel.setText("Supporting reference");
                infoLabel.setText("Keep part of the deposit (damage, arrears). Available: " + fmt.money(balance) + ". The reason is kept on record.");
            }
            case REFUND -> {
                show(false, reasonLabel, reasonField);
                infoLabel.setText("Return money to " + position.tenantName() + ". Available: " + fmt.money(balance) + ". Partial refunds are fine.");
                amountField.setText(balance.toPlainString());
            }
        }
    }

    private static void show(boolean visible, Node... nodes) {
        for (Node n : nodes) {
            n.setVisible(visible);
            n.setManaged(visible);
        }
    }

    @FXML
    private void onSave() {
        try {
            BigDecimal amount = InputParser.requiredMoney("amount", "Amount", amountField.getText());
            LocalDate date = InputParser.requiredDate("date", "Date", datePicker.getValue());
            String ref = InputParser.optionalText(referenceField.getText());
            String notes = InputParser.optionalText(notesArea.getText());
            long id = position.agreementId();
            String question = switch (mode) {
                case COLLECT -> null;
                case DEDUCT -> "Deduct " + fmt.money(amount) + " from the deposit? This reduces what the tenant can get back.";
                case REFUND -> "Refund " + fmt.money(amount) + " to " + position.tenantName() + "?";
            };
            if (question != null && !AlertUtil.confirm(mode == Mode.DEDUCT ? "Deduct from deposit" : "Refund deposit", question)) {
                return;
            }
            UiTask.run(() -> switch (mode) {
                case COLLECT -> ctx.deposits().collect(id, amount, date, methodBox.getValue(), ref, notes);
                case DEDUCT -> ctx.deposits().deduct(id, amount, date, reasonField.getText(), ref, notes);
                case REFUND -> ctx.deposits().refund(id, amount, date, methodBox.getValue(), ref, notes);
            }, saved -> {
                close();
                nav.toast("Deposit updated");
                onSaved.run();
            }, saveButton);
        } catch (EstateFlowException e) {
            ErrorHandler.handle(e);
        }
    }

    @FXML
    private void onCancel() {
        close();
    }
}
