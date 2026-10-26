package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.model.Tenant;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

public class TenantFormController extends AbstractDialogController {

    @FXML private TextField nameField, phoneField, emailField, addressField, idNumberField, emergencyNameField, emergencyPhoneField;
    @FXML private ComboBox<String> idTypeBox;
    @FXML private Button saveButton;
    private Tenant existing;
    private Runnable onSaved = () -> { };

    public TenantFormController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        idTypeBox.getItems().setAll("AADHAAR", "PAN", "PASSPORT", "DRIVING_LICENCE", "VOTER_ID");
    }

    void init(Tenant tenant, Runnable onSaved) {
        this.existing = tenant;
        this.onSaved = onSaved;
        if (tenant != null) {
            nameField.setText(tenant.getFullName());
            phoneField.setText(tenant.getPhone());
            emailField.setText(tenant.getEmail());
            addressField.setText(tenant.getAddress());
            idTypeBox.setValue(tenant.getIdProofType());
            idNumberField.setText(tenant.getIdProofNumber());
            emergencyNameField.setText(tenant.getEmergencyContactName());
            emergencyPhoneField.setText(tenant.getEmergencyContactPhone());
        }
    }

    @FXML
    private void onSave() {
        try {
            Tenant tenant = new Tenant(InputParser.requiredText("fullName", "Name", nameField.getText()),
                    phoneField.getText(), InputParser.optionalText(emailField.getText()),
                    InputParser.optionalText(addressField.getText()));
            tenant.setId(existing == null ? null : existing.getId());
            tenant.setIdProofType(InputParser.optionalText(idTypeBox.getEditor().getText()));
            tenant.setIdProofNumber(InputParser.optionalText(idNumberField.getText()));
            tenant.setEmergencyContactName(InputParser.optionalText(emergencyNameField.getText()));
            tenant.setEmergencyContactPhone(InputParser.optionalText(emergencyPhoneField.getText()));
            UiTask.run(() -> existing == null ? ctx.tenants().create(tenant) : ctx.tenants().update(tenant), saved -> {
                close();
                nav.toast("Tenant saved");
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
