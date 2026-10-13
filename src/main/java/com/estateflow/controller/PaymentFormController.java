package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.model.RentCharge;
import com.estateflow.model.enums.PaymentMethod;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PaymentFormController extends AbstractDialogController {

    @FXML private Label chargeLabel;
    @FXML private TextField amountField, referenceField, notesField;
    @FXML private DatePicker datePicker;
    @FXML private ComboBox<PaymentMethod> methodBox;
    @FXML private Button saveButton;
    private RentCharge charge;
    private Runnable onSaved = () -> { };

    public PaymentFormController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        methodBox.getItems().setAll(PaymentMethod.values());
        methodBox.setValue(PaymentMethod.CASH);
        datePicker.setValue(LocalDate.now(ctx.clock()));
    }

    void init(RentCharge charge, Runnable onSaved) {
        this.charge = charge;
        this.onSaved = onSaved;
        chargeLabel.setText("Period " + DateFormats.format(charge.getPeriodStart()) + " - " + DateFormats.format(charge.getPeriodEnd())
                + "\nDue " + DateFormats.format(charge.getDueDate()) + ", balance " + MoneyUtil.format(charge.getBalance()));
        amountField.setText(charge.getBalance().toPlainString());
    }

    @FXML
    private void onSave() {
        try {
            BigDecimal amount = InputParser.requiredMoney("amount", "Amount", amountField.getText());
            LocalDate date = InputParser.requiredDate("paymentDate", "Payment date", datePicker.getValue());
            PaymentMethod method = methodBox.getValue();
            String reference = InputParser.optionalText(referenceField.getText());
            String notes = InputParser.optionalText(notesField.getText());
            UiTask.run(() -> ctx.payments().recordPayment(charge.getChargeId(), amount, date, method, reference, notes), p -> {
                close();
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
