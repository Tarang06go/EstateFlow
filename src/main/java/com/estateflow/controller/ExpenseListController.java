package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.ExpenseRow;
import com.estateflow.model.Property;
import com.estateflow.model.enums.ExpenseCategory;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Expense list with filters. Writes are hidden for read-only roles and enforced again in ExpenseService. */
public class ExpenseListController extends AbstractController {

    @FXML private ComboBox<Property> propertyBox;
    @FXML private ComboBox<ExpenseCategory> categoryBox;
    @FXML private DatePicker fromPicker, toPicker;
    @FXML private CheckBox deletedBox;
    @FXML private Label countLabel;
    @FXML private TableView<ExpenseRow> table;

    public ExpenseListController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        categoryBox.getItems().setAll(ExpenseCategory.values());
        table.setPlaceholder(new Label("No expenses match these filters."));
        TableUtil.column(table, "Date", 105, r -> fmt.date(r.expense().getExpenseDate()));
        TableUtil.column(table, "Property", 160, ExpenseRow::propertyName);
        TableUtil.column(table, "Category", 120, r -> r.expense().getCategory().getDisplayLabel());
        TableUtil.column(table, "Description", 230, r -> r.expense().getDescription());
        TableUtil.column(table, "Method", 105, r -> r.expense().getPaymentMethod().getDisplayLabel());
        TableUtil.column(table, "Reference", 105, r -> r.expense().getReferenceNo());
        TableUtil.column(table, "Maintenance request", 170, r -> r.maintenanceTitle());
        TableUtil.column(table, "Amount", 105, r -> fmt.money(r.expense().getAmount()));
        TableUtil.column(table, "Status", 190, r -> r.expense().isDeleted() ? "DELETED: " + r.expense().getDeleteReason() : "Active");
        table.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(ExpenseRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().remove("row-void");
                if (!empty && row != null && row.expense().isDeleted()) {
                    getStyleClass().add("row-void");
                }
            }
        });
        UiTask.run(() -> {
            ctx.expenses().ensureReady();
            return ctx.properties().findAll();
        }, props -> {
            propertyBox.getItems().setAll(props);
            reload();
        }, failure -> {
            table.setPlaceholder(new Label("Expenses are unavailable until the v1.4 migration is applied."));
            ErrorHandler.handle(failure);
        });
    }

    @FXML
    void reload() {
        Long propertyId = propertyBox.getValue() == null ? null : propertyBox.getValue().getPropertyId();
        ExpenseCategory category = categoryBox.getValue();
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        boolean deleted = deletedBox.isSelected();
        UiTask.run(() -> ctx.expenses().search(propertyId, category, from, to, deleted), rows -> {
            table.getItems().setAll(rows);
            BigDecimal total = rows.stream().filter(r -> !r.expense().isDeleted()).map(r -> r.expense().getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            countLabel.setText(rows.size() + " expense(s) shown (newest first, max 1000)  |  active total " + fmt.money(total));
        });
    }

    @FXML
    private void onClear() {
        propertyBox.setValue(null);
        categoryBox.setValue(null);
        fromPicker.setValue(null);
        toPicker.setValue(null);
        deletedBox.setSelected(false);
        reload();
    }

    @FXML
    private void onAdd() {
        nav.<ExpenseFormController>showDialog("expense-form.fxml", "Add expense", c -> c.init(null, this::reload));
    }

    @FXML
    private void onEdit() {
        ExpenseRow row = selected("Edit expense");
        if (row == null) {
            return;
        }
        if (row.expense().isDeleted()) {
            AlertUtil.info("Edit expense", "A deleted expense cannot be edited.");
            return;
        }
        nav.<ExpenseFormController>showDialog("expense-form.fxml", "Edit expense", c -> c.init(row.expense(), this::reload));
    }

    @FXML
    private void onDelete() {
        ExpenseRow row = selected("Delete expense");
        if (row == null) {
            return;
        }
        if (row.expense().isDeleted()) {
            AlertUtil.info("Delete expense", "This expense is already deleted.");
            return;
        }
        nav.<DeleteExpenseController>showDialog("delete-expense.fxml", "Delete expense", c -> c.init(row, this::reload));
    }

    private ExpenseRow selected(String title) {
        ExpenseRow row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            AlertUtil.info(title, "Select an expense first.");
        }
        return row;
    }
}
