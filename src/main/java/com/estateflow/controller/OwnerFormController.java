package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.model.Owner;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

public class OwnerFormController extends AbstractDialogController {

    @FXML private TextField nameField, phoneField, emailField, addressField;
    @FXML private Button saveButton;
    private Owner existing;
    private Runnable onSaved = () -> { };

    public OwnerFormController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    void init(Owner owner, Runnable onSaved) {
        this.existing = owner;
        this.onSaved = onSaved;
        if (owner != null) {
            nameField.setText(owner.getFullName());
            phoneField.setText(owner.getPhone());
            emailField.setText(owner.getEmail());
            addressField.setText(owner.getAddress());
        }
    }

    @FXML
    private void onSave() {
        try {
            Owner owner = new Owner(InputParser.requiredText("fullName", "Name", nameField.getText()),
                    phoneField.getText(), InputParser.optionalText(emailField.getText()),
                    InputParser.optionalText(addressField.getText()));
            owner.setId(existing == null ? null : existing.getId());
            UiTask.run(() -> existing == null ? ctx.owners().create(owner) : ctx.owners().update(owner), saved -> {
                close();
                nav.toast("Owner saved");
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
