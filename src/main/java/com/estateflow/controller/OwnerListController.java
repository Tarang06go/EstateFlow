package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.model.Owner;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class OwnerListController extends AbstractController {

    @FXML private TextField searchField;
    @FXML private TableView<Owner> table;
    @FXML private Button toggleButton;

    public OwnerListController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        TableUtil.column(table, "Name", 190, Owner::getFullName);
        TableUtil.column(table, "Phone", 130, Owner::getPhone);
        TableUtil.column(table, "Email", 190, Owner::getEmail);
        TableUtil.column(table, "Address", 240, Owner::getAddress);
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
        UiTask.run(() -> ctx.owners().search(query), rows -> table.getItems().setAll(rows));
    }

    @FXML
    private void onAdd() {
        nav.<OwnerFormController>showDialog("owner-form.fxml", "Add owner", c -> c.init(null, this::reload));
    }

    @FXML
    private void onProfile() {
        Owner selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.info("Profile", "Select a row first.");
            return;
        }
        nav.<OwnerDetailController>showDialog("owner-detail.fxml", "Owner profile", c -> c.init(selected.getId()));
    }

    @FXML
    private void onEdit() {
        Owner selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.info("Edit owner", "Select an owner first.");
            return;
        }
        nav.<OwnerFormController>showDialog("owner-form.fxml", "Edit owner", c -> c.init(selected, this::reload));
    }

    @FXML
    private void onToggleActive() {
        Owner selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.info("Owner", "Select an owner first.");
            return;
        }
        if (selected.isActive()) {
            if (!AlertUtil.confirm("Deactivate owner", "Deactivate " + selected.getFullName() + "?")) {
                return;
            }
            UiTask.runVoid(() -> ctx.owners().deactivate(selected.getId()), this::reload, toggleButton);
        } else {
            UiTask.runVoid(() -> ctx.owners().reactivate(selected.getId()), this::reload, toggleButton);
        }
    }
}
