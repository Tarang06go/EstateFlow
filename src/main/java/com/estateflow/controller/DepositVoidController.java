package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.model.DepositTransaction;
import com.estateflow.util.SceneNavigator;
import com.estateflow.util.UiTask;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

/** Voids one ledger entry after asking for a reason. */
public class DepositVoidController extends AbstractDialogController {

    @FXML private Label infoLabel;
    @FXML private TextArea reasonArea;
    @FXML private Button voidButton;

    private DepositTransaction txn;
    private Runnable onVoided = () -> { };

    public DepositVoidController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    void init(DepositTransaction txn, Runnable onVoided) {
        this.txn = txn;
        this.onVoided = onVoided;
        infoLabel.setText(txn.type().getDisplayLabel() + " of " + fmt.money(txn.amount()) + " dated " + fmt.date(txn.date()));
    }

    @FXML
    private void onVoid() {
        String reason = reasonArea.getText();
        UiTask.run(() -> ctx.deposits().voidTransaction(txn.txnId(), reason), row -> {
            close();
            nav.toast("Entry voided");
            onVoided.run();
        }, voidButton);
    }

    @FXML
    private void onCancel() {
        close();
    }
}
