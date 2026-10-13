package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.model.Property;
import com.estateflow.model.Tenant;
import com.estateflow.service.rules.RentScheduleGenerator;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public class AgreementFormController extends AbstractDialogController {

    @FXML private ComboBox<Property> propertyBox;
    @FXML private ComboBox<Tenant> tenantBox;
    @FXML private DatePicker startPicker;
    @FXML private TextField monthsField, rentField, depositField, notesField;
    @FXML private Label previewLabel;
    @FXML private Button saveButton;
    private Runnable onSaved = () -> { };

    public AgreementFormController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        UiTask.run(() -> ctx.properties().findAll().stream().filter(Property::isActive).toList(),
                list -> propertyBox.getItems().setAll(list));
        UiTask.run(() -> ctx.tenants().findAll().stream().filter(Tenant::isActive).toList(),
                list -> tenantBox.getItems().setAll(list));
        startPicker.setValue(LocalDate.now(ctx.clock()));
        propertyBox.valueProperty().addListener((o, a, p) -> {
            if (p != null) {
                rentField.setText(p.getListedRent().toPlainString());
            }
        });
        startPicker.valueProperty().addListener((o, a, b) -> preview());
        monthsField.textProperty().addListener((o, a, b) -> preview());
        rentField.textProperty().addListener((o, a, b) -> preview());
    }

    void init(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    /** BR-11/BR-14 preview: pure calculation, no database access. */
    private void preview() {
        try {
            int months = InputParser.requiredInt("durationMonths", "Duration", monthsField.getText());
            LocalDate start = InputParser.requiredDate("startDate", "Start date", startPicker.getValue());
            BigDecimal rent = InputParser.requiredMoney("monthlyRent", "Monthly rent", rentField.getText());
            if (months < 1 || months > 120) {
                previewLabel.setText("");
                return;
            }
            LocalDate end = RentScheduleGenerator.endDate(start, months);
            previewLabel.setText(months + " monthly rent charges of " + MoneyUtil.format(rent) + ", total "
                    + MoneyUtil.format(rent.multiply(BigDecimal.valueOf(months))) + ". Runs "
                    + DateFormats.format(start) + " to " + DateFormats.format(end) + ". Rent is due on the first day of each period.");
        } catch (EstateFlowException e) {
            previewLabel.setText("");
        }
    }

    @FXML
    private void onSave() {
        try {
            Property property = propertyBox.getValue();
            Tenant tenant = tenantBox.getValue();
            LocalDate start = InputParser.requiredDate("startDate", "Start date", startPicker.getValue());
            int months = InputParser.requiredInt("durationMonths", "Duration", monthsField.getText());
            BigDecimal rent = InputParser.requiredMoney("monthlyRent", "Monthly rent", rentField.getText());
            BigDecimal deposit = InputParser.optionalMoney("securityDeposit", "Security deposit", depositField.getText());
            String notes = InputParser.optionalText(notesField.getText());
            Long propertyId = property == null ? null : property.getPropertyId();
            Long tenantId = tenant == null ? null : tenant.getId();
            UiTask.run(() -> ctx.agreements().create(propertyId, tenantId, start, months, rent, deposit, notes), saved -> {
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
