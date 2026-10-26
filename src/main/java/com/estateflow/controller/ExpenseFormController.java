package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.LinkableMaintenance;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.model.Expense;
import com.estateflow.model.Property;
import com.estateflow.model.enums.ExpenseCategory;
import com.estateflow.model.enums.PaymentMethod;
import com.estateflow.util.ErrorHandler;
import com.estateflow.util.InputParser;
import com.estateflow.util.SceneNavigator;
import com.estateflow.util.UiTask;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDate;

/** Add or edit one expense. Validation happens again in ExpenseService; this only gives early, clear messages. */
public class ExpenseFormController extends AbstractDialogController {

    @FXML private ComboBox<Property> propertyBox;
    @FXML private ComboBox<ExpenseCategory> categoryBox;
    @FXML private ComboBox<PaymentMethod> methodBox;
    @FXML private ComboBox<LinkableMaintenance> requestBox;
    @FXML private DatePicker datePicker;
    @FXML private TextField amountField, referenceField;
    @FXML private TextArea descriptionArea;
    @FXML private Button saveButton;

    private Expense existing;
    private Runnable onSaved = () -> { };

    public ExpenseFormController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        categoryBox.getItems().setAll(ExpenseCategory.values());
        categoryBox.setValue(ExpenseCategory.MAINTENANCE);
        methodBox.getItems().setAll(PaymentMethod.values());
        methodBox.setValue(PaymentMethod.CASH);
        datePicker.setValue(LocalDate.now(ctx.clock()));
        UiTask.run(() -> ctx.properties().findAll(), list -> {
            propertyBox.getItems().setAll(list);
            selectExisting();
        });
        propertyBox.valueProperty().addListener((o, was, now) -> loadRequests());
        requestBox.valueProperty().addListener((o, was, picked) -> {
            if (picked != null) {
                if (categoryBox.getValue() != ExpenseCategory.MAINTENANCE && categoryBox.getValue() != ExpenseCategory.REPAIRS) {
                    categoryBox.setValue(ExpenseCategory.MAINTENANCE);
                }
                if ((amountField.getText() == null || amountField.getText().isBlank()) && picked.cost() != null) {
                    amountField.setText(picked.cost().toPlainString());
                }
            }
        });
    }

    private void loadRequests() {
        Property property = propertyBox.getValue();
        if (property == null) {
            requestBox.getItems().clear();
            return;
        }
        Long keep = existing == null ? null : existing.getMaintenanceRequestId();
        UiTask.run(() -> ctx.expenses().linkableMaintenance(property.getPropertyId(), keep), list -> {
            requestBox.getItems().setAll(list);
            if (keep != null) {
                list.stream().filter(r -> r.requestId() == keep).findFirst().ifPresent(requestBox::setValue);
            }
        });
    }

    private void selectExisting() {
        if (existing != null) {
            propertyBox.getItems().stream().filter(p -> p.getPropertyId().equals(existing.getPropertyId())).findFirst()
                    .ifPresent(propertyBox::setValue);
        }
    }

    void init(Expense expense, Runnable onSaved) {
        this.existing = expense;
        this.onSaved = onSaved;
        if (expense != null) {
            selectExisting();
            categoryBox.setValue(expense.getCategory());
            datePicker.setValue(expense.getExpenseDate());
            amountField.setText(expense.getAmount().toPlainString());
            methodBox.setValue(expense.getPaymentMethod());
            referenceField.setText(expense.getReferenceNo());
            descriptionArea.setText(expense.getDescription());
            propertyBox.setDisable(true); // an expense stays with its property; delete and re-enter to move it
            loadRequests();
        }
    }

    @FXML
    private void onSave() {
        try {
            Expense e = new Expense();
            e.setExpenseId(existing == null ? null : existing.getExpenseId());
            e.setPropertyId(propertyBox.getValue() == null ? null : propertyBox.getValue().getPropertyId());
            e.setCategory(categoryBox.getValue());
            e.setExpenseDate(InputParser.requiredDate("expenseDate", "Expense date", datePicker.getValue()));
            e.setAmount(InputParser.requiredMoney("amount", "Amount", amountField.getText()));
            e.setPaymentMethod(methodBox.getValue());
            e.setReferenceNo(InputParser.optionalText(referenceField.getText()));
            e.setDescription(InputParser.requiredText("description", "Description", descriptionArea.getText()));
            e.setMaintenanceRequestId(requestBox.getValue() == null ? null : requestBox.getValue().requestId());
            UiTask.run(() -> existing == null ? ctx.expenses().create(e) : ctx.expenses().update(e), saved -> {
                close();
                nav.toast("Expense saved");
                onSaved.run();
            }, saveButton);
        } catch (EstateFlowException ex) {
            ErrorHandler.handle(ex);
        }
    }

    @FXML
    private void onClearRequest() {
        requestBox.setValue(null);
    }

    @FXML
    private void onCancel() {
        close();
    }
}
