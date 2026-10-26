package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.PropertyOccupancyRow;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class PropertyListController extends AbstractController {

    @FXML private TextField searchField;
    @FXML private TableView<PropertyOccupancyRow> table;
    @FXML private Button toggleButton;

    public PropertyListController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        TableUtil.column(table, "Property", 170, PropertyOccupancyRow::propertyName);
        TableUtil.column(table, "City", 110, PropertyOccupancyRow::city);
        TableUtil.column(table, "Owner", 160, PropertyOccupancyRow::ownerName);
        TableUtil.column(table, "Occupancy", 100, r -> r.occupied() ? "Occupied" : "Vacant");
        TableUtil.column(table, "Tenant today", 160, PropertyOccupancyRow::tenantName);
        TableUtil.column(table, "Listed rent", 110, r -> fmt.money(r.listedRent()));
        TableUtil.column(table, "Status", 80, r -> r.active() ? "Active" : "Inactive");
        reload();
    }

    @FXML
    void reload() {
        String query = searchField.getText();
        UiTask.run(() -> ctx.properties().findRows(query), rows -> table.getItems().setAll(rows));
    }

    private PropertyOccupancyRow selected() {
        PropertyOccupancyRow row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            AlertUtil.info("Property", "Select a property first.");
        }
        return row;
    }

    @FXML
    private void onAdd() {
        nav.<PropertyFormController>showDialog("property-form.fxml", "Add property", c -> c.init(null, this::reload));
    }

    @FXML
    private void onEdit() {
        PropertyOccupancyRow row = selected();
        if (row != null) {
            UiTask.run(() -> ctx.properties().get(row.propertyId()), property ->
                    nav.<PropertyFormController>showDialog("property-form.fxml", "Edit property", c -> c.init(property, this::reload)));
        }
    }

    @FXML
    private void onDetails() {
        PropertyOccupancyRow row = selected();
        if (row != null) {
            nav.<PropertyDetailController>showDialog("property-detail.fxml", "Property details", c -> c.init(row.propertyId()));
        }
    }

    @FXML
    private void onToggleActive() {
        PropertyOccupancyRow row = selected();
        if (row == null) {
            return;
        }
        if (row.active()) {
            if (AlertUtil.confirm("Deactivate property", "Deactivate " + row.propertyName() + "?")) {
                UiTask.runVoid(() -> ctx.properties().deactivate(row.propertyId()), this::reload, toggleButton);
            }
        } else {
            UiTask.runVoid(() -> ctx.properties().reactivate(row.propertyId()), this::reload, toggleButton);
        }
    }
}
