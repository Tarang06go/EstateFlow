package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.ExpenseRow;
import com.estateflow.util.SceneNavigator;
import com.estateflow.util.UiTask;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

/** Asks for a reason, then soft-deletes the expense. */
public class DeleteExpenseController extends AbstractDialogController {

    @FXML private Label infoLabel;
    @FXML private TextArea reasonArea;
    @FXML private Button deleteButton;

    private ExpenseRow row;
    private Runnable onDeleted = () -> { };

    public DeleteExpenseController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    void init(ExpenseRow row, Runnable onDeleted) {
        this.row = row;
        this.onDeleted = onDeleted;
        infoLabel.setText(row.expense().getCategory().getDisplayLabel() + " expense of " + fmt.money(row.expense().getAmount())
                + " on " + row.propertyName() + ", dated " + fmt.date(row.expense().getExpenseDate()));
    }

    @FXML
    private void onDelete() {
        String reason = reasonArea.getText();
        UiTask.runVoid(() -> ctx.expenses().delete(row.expense().getExpenseId(), reason), () -> {
            close();
            nav.toast("Expense deleted");
            onDeleted.run();
        }, deleteButton);
    }

    @FXML
    private void onCancel() {
        close();
    }
}
