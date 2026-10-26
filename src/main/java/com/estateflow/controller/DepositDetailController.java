package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.DepositRow;
import com.estateflow.model.DepositTransaction;
import com.estateflow.util.AlertUtil;
import com.estateflow.util.SceneNavigator;
import com.estateflow.util.TableUtil;
import com.estateflow.util.UiTask;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

import java.util.List;

/** One agreement's deposit: position, full ledger (voided entries stay visible) and the actions on it. */
public class DepositDetailController extends AbstractDialogController {

    public enum Mode { COLLECT, DEDUCT, REFUND }

    @FXML private Label headerLabel, summaryLabel, hintLabel;
    @FXML private TableView<DepositTransaction> table;
    @FXML private Button collectButton;

    private long agreementId;
    private DepositRow position;
    private Runnable onChanged = () -> { };

    public DepositDetailController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        table.setPlaceholder(new Label("Nothing has been collected for this agreement yet."));
        TableUtil.column(table, "Date", 95, t -> fmt.date(t.date()));
        TableUtil.column(table, "Type", 95, t -> t.type().getDisplayLabel());
        TableUtil.column(table, "Amount", 100, t -> fmt.money(t.amount()));
        TableUtil.column(table, "Method", 100, t -> t.method() == null ? "" : t.method().getDisplayLabel());
        TableUtil.column(table, "Reference", 100, DepositTransaction::referenceNo);
        TableUtil.column(table, "Reason / notes", 190, t -> t.reason() != null ? t.reason() : t.notes());
        TableUtil.column(table, "Status", 150, t -> t.isVoided() ? "VOID: " + t.voidReason() : "");
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(DepositTransaction t, boolean empty) {
                super.updateItem(t, empty);
                getStyleClass().remove("row-void");
                if (!empty && t != null && t.isVoided()) {
                    getStyleClass().add("row-void");
                }
            }
        });
    }

    void init(long agreementId, Runnable onChanged) {
        this.agreementId = agreementId;
        this.onChanged = onChanged;
        load();
    }

    private void load() {
        UiTask.run(() -> new Object[]{ctx.deposits().findPosition(agreementId).orElseThrow(), ctx.deposits().transactions(agreementId)}, data -> {
            position = (DepositRow) data[0];
            @SuppressWarnings("unchecked") List<DepositTransaction> txns = (List<DepositTransaction>) data[1];
            headerLabel.setText("Agreement #" + position.agreementId() + " - " + position.propertyName() + " - " + position.tenantName());
            var t = position.totals();
            summaryLabel.setText("Required by the lease: " + fmt.money(position.required()) + "     Held (original): " + fmt.money(t.original())
                    + "\nDeductions: " + fmt.money(t.deducted()) + "     Refunds: " + fmt.money(t.refunded())
                    + "     Carried to renewal: " + fmt.money(t.transferredOut())
                    + "\nRefundable balance: " + fmt.money(t.balance()) + "     Status: " + position.status().getDisplayLabel());
            boolean ended = position.ended(java.time.LocalDate.now(ctx.clock()));
            hintLabel.setText(ended ? "The lease has ended: the balance can be deducted from or refunded."
                    : "Refunds become possible after the lease ends (" + fmt.date(position.effectiveEnd()) + "). Deductions can be recorded any time.");
            collectButton.setDisable(java.time.LocalDate.now(ctx.clock()).isAfter(position.effectiveEnd()));
            table.getItems().setAll(txns);
        });
    }

    private void open(Mode mode) {
        if (position == null) {
            return;
        }
        nav.<DepositFormController>showDialog("deposit-form.fxml", switch (mode) {
            case COLLECT -> "Collect deposit";
            case DEDUCT -> "Deduct from deposit";
            case REFUND -> "Refund deposit";
        }, c -> c.init(position, mode, () -> {
            onChanged.run();
            load();
        }));
    }

    @FXML
    private void onCollect() {
        open(Mode.COLLECT);
    }

    @FXML
    private void onDeduct() {
        open(Mode.DEDUCT);
    }

    @FXML
    private void onRefund() {
        open(Mode.REFUND);
    }

    @FXML
    private void onVoid() {
        DepositTransaction t = table.getSelectionModel().getSelectedItem();
        if (t == null) {
            AlertUtil.info("Void entry", "Select a ledger entry first.");
            return;
        }
        if (t.isVoided()) {
            AlertUtil.info("Void entry", "This entry is already void.");
            return;
        }
        nav.<DepositVoidController>showDialog("deposit-void.fxml", "Void deposit entry", c -> c.init(t, () -> {
            onChanged.run();
            load();
        }));
    }

    @FXML
    private void onClose() {
        close();
    }
}
