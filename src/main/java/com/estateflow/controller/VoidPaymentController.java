package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.model.Payment;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

public class VoidPaymentController extends AbstractDialogController {

    @FXML private Label infoLabel;
    @FXML private TextArea reasonArea;
    @FXML private Button voidButton;
    private Payment payment;
    private Runnable onVoided = () -> { };

    public VoidPaymentController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    void init(Payment payment, Runnable onVoided) {
        this.payment = payment;
        this.onVoided = onVoided;
        infoLabel.setText("Payment " + payment.receiptNumber() + " of " + MoneyUtil.format(payment.getAmount())
                + " dated " + DateFormats.format(payment.getPaymentDate()));
    }

    @FXML
    private void onVoid() {
        String reason = reasonArea.getText();
        UiTask.runVoid(() -> ctx.payments().voidPayment(payment.getPaymentId(), reason), () -> {
            close();
            onVoided.run();
        }, voidButton);
    }

    @FXML
    private void onCancel() {
        close();
    }
}
