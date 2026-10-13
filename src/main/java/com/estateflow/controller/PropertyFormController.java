package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.model.Owner;
import com.estateflow.model.Property;
import com.estateflow.model.enums.PropertyType;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

public class PropertyFormController extends AbstractDialogController {

    @FXML private ComboBox<Owner> ownerBox;
    @FXML private ComboBox<PropertyType> typeBox;
    @FXML private TextField nameField, addressField, cityField, stateField, postalField, bedroomsField, areaField, rentField;
    @FXML private Button saveButton;
    private Property existing;
    private Runnable onSaved = () -> { };

    public PropertyFormController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        typeBox.getItems().setAll(PropertyType.values());
        UiTask.run(() -> ctx.owners().findAll().stream().filter(Owner::isActive).toList(), owners -> {
            ownerBox.getItems().setAll(owners);
            if (existing != null) {
                owners.stream().filter(o -> o.getId().equals(existing.getOwnerId())).findFirst().ifPresent(ownerBox::setValue);
            }
        });
    }

    void init(Property property, Runnable onSaved) {
        this.existing = property;
        this.onSaved = onSaved;
        if (property != null) {
            ownerBox.getItems().stream().filter(o -> o.getId().equals(property.getOwnerId())).findFirst().ifPresent(ownerBox::setValue);
            nameField.setText(property.getName());
            typeBox.setValue(property.getPropertyType());
            addressField.setText(property.getAddressLine());
            cityField.setText(property.getCity());
            stateField.setText(property.getState());
            postalField.setText(property.getPostalCode());
            bedroomsField.setText(property.getBedrooms() == null ? "" : String.valueOf(property.getBedrooms()));
            areaField.setText(property.getAreaSqft() == null ? "" : property.getAreaSqft().toPlainString());
            rentField.setText(property.getListedRent().toPlainString());
        }
    }

    @FXML
    private void onSave() {
        try {
            Property p = new Property();
            p.setPropertyId(existing == null ? null : existing.getPropertyId());
            p.setOwnerId(ownerBox.getValue() == null ? null : ownerBox.getValue().getId());
            p.setName(InputParser.requiredText("name", "Name", nameField.getText()));
            p.setPropertyType(typeBox.getValue());
            p.setAddressLine(InputParser.requiredText("addressLine", "Address", addressField.getText()));
            p.setCity(InputParser.requiredText("city", "City", cityField.getText()));
            p.setState(InputParser.optionalText(stateField.getText()));
            p.setPostalCode(InputParser.optionalText(postalField.getText()));
            p.setBedrooms(InputParser.optionalInt("bedrooms", "Bedrooms", bedroomsField.getText()));
            p.setAreaSqft(InputParser.optionalMoney("areaSqft", "Area", areaField.getText()));
            p.setListedRent(InputParser.requiredMoney("listedRent", "Listed rent", rentField.getText()));
            UiTask.run(() -> existing == null ? ctx.properties().create(p) : ctx.properties().update(p), saved -> {
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
