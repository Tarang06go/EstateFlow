package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.model.MaintenanceRequest;
import com.estateflow.model.Property;
import com.estateflow.model.Tenant;
import com.estateflow.model.enums.MaintenancePriority;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDate;

public class MaintenanceFormController extends AbstractDialogController {

    @FXML private ComboBox<Property> propertyBox;
    @FXML private ComboBox<Tenant> tenantBox;
    @FXML private ComboBox<MaintenancePriority> priorityBox;
    @FXML private TextField titleField, assignedField, costField;
    @FXML private TextArea descriptionArea;
    @FXML private DatePicker reportedPicker;
    @FXML private Button saveButton;
    private MaintenanceRequest existing;
    private Runnable onSaved = () -> { };

    public MaintenanceFormController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        priorityBox.getItems().setAll(MaintenancePriority.values());
        priorityBox.setValue(MaintenancePriority.MEDIUM);
        reportedPicker.setValue(LocalDate.now(ctx.clock()));
        UiTask.run(() -> ctx.properties().findAll().stream().filter(Property::isActive).toList(), list -> {
            propertyBox.getItems().setAll(list);
            select();
        });
        UiTask.run(() -> ctx.tenants().findAll(), list -> {
            tenantBox.getItems().setAll(list);
            select();
        });
    }

    private void select() {
        if (existing != null) {
            propertyBox.getItems().stream().filter(p -> p.getPropertyId().equals(existing.getPropertyId())).findFirst().ifPresent(propertyBox::setValue);
            tenantBox.getItems().stream().filter(t -> t.getId().equals(existing.getTenantId())).findFirst().ifPresent(tenantBox::setValue);
        }
    }

    void init(MaintenanceRequest request, Runnable onSaved) {
        this.existing = request;
        this.onSaved = onSaved;
        if (request != null) {
            select();
            titleField.setText(request.getTitle());
            descriptionArea.setText(request.getDescription());
            priorityBox.setValue(request.getPriority());
            reportedPicker.setValue(request.getReportedDate());
            assignedField.setText(request.getAssignedTo());
            costField.setText(request.getCost() == null ? "" : request.getCost().toPlainString());
            propertyBox.setDisable(true);
            tenantBox.setDisable(true);
            reportedPicker.setDisable(true);
        }
    }

    @FXML
    private void onSave() {
        try {
            MaintenanceRequest r = new MaintenanceRequest();
            r.setRequestId(existing == null ? null : existing.getRequestId());
            r.setPropertyId(propertyBox.getValue() == null ? null : propertyBox.getValue().getPropertyId());
            r.setTenantId(tenantBox.getValue() == null ? null : tenantBox.getValue().getId());
            r.setTitle(InputParser.requiredText("title", "Title", titleField.getText()));
            r.setDescription(InputParser.optionalText(descriptionArea.getText()));
            r.setPriority(priorityBox.getValue());
            r.setReportedDate(InputParser.requiredDate("reportedDate", "Reported date", reportedPicker.getValue()));
            r.setAssignedTo(InputParser.optionalText(assignedField.getText()));
            r.setCost(InputParser.optionalMoney("cost", "Cost", costField.getText()));
            UiTask.run(() -> existing == null ? ctx.maintenance().open(r) : ctx.maintenance().update(r), saved -> {
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
