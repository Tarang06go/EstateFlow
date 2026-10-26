package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.model.Tenant;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class TenantListController extends AbstractController {

    @FXML private TextField searchField;
    @FXML private TableView<Tenant> table;
    @FXML private Button toggleButton;

    public TenantListController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        TableUtil.column(table, "Name", 190, Tenant::getFullName);
        TableUtil.column(table, "Phone", 130, Tenant::getPhone);
        TableUtil.column(table, "Email", 190, Tenant::getEmail);
        TableUtil.column(table, "ID proof", 170, t -> t.getIdProofType() == null ? "" : t.getIdProofType() + " " + com.estateflow.util.Masking.idNumber(t.getIdProofNumber()));
        TableUtil.column(table, "Status", 80, o -> o.isActive() ? "Active" : "Inactive");
        table.setPlaceholder(new Label("Nothing here yet. Use the Add button to create the first record."));
        table.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY && e.getClickCount() == 2) {
                onProfile();
            }
        });
        reload();
    }

    @FXML
    void reload() {
        String query = searchField.getText();
        UiTask.run(() -> ctx.tenants().search(query), rows -> table.getItems().setAll(rows));
    }

    @FXML
    private void onAdd() {
        nav.<TenantFormController>showDialog("tenant-form.fxml", "Add tenant", c -> c.init(null, this::reload));
    }

    @FXML
    private void onProfile() {
        Tenant selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.info("Profile", "Select a row first.");
            return;
        }
        nav.<TenantDetailController>showDialog("tenant-detail.fxml", "Tenant profile", c -> c.init(selected.getId()));
    }

    @FXML
    private void onEdit() {
        Tenant selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.info("Edit tenant", "Select a tenant first.");
            return;
        }
        nav.<TenantFormController>showDialog("tenant-form.fxml", "Edit tenant", c -> c.init(selected, this::reload));
    }

    @FXML
    private void onToggleActive() {
        Tenant selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.info("Tenant", "Select a tenant first.");
            return;
        }
        if (selected.isActive()) {
            if (!AlertUtil.confirm("Deactivate tenant", "Deactivate " + selected.getFullName() + "?")) {
                return;
            }
            UiTask.runVoid(() -> ctx.tenants().deactivate(selected.getId()), this::reload, toggleButton);
        } else {
            UiTask.runVoid(() -> ctx.tenants().reactivate(selected.getId()), this::reload, toggleButton);
        }
    }
}
