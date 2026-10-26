package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.model.AppUser;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

import java.time.format.DateTimeFormatter;

/** Administrator-only user management screen. Authorization is enforced again in UserManagementService. */
public class UserListController extends AbstractController {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private TableView<AppUser> table;
    @FXML private Button toggleButton;

    public UserListController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        TableUtil.column(table, "Username", 190, AppUser::username);
        TableUtil.column(table, "Role", 120, u -> u.role().label());
        TableUtil.column(table, "Status", 90, u -> u.active() ? "Active" : "Inactive");
        TableUtil.column(table, "Last sign-in (UTC)", 160, u -> u.lastLoginAt() == null ? "Never" : STAMP.format(u.lastLoginAt()));
        TableUtil.column(table, "Created (UTC)", 160, u -> u.createdAt() == null ? "" : STAMP.format(u.createdAt()));
        table.setPlaceholder(new Label("No users yet."));
        reload();
    }

    @FXML
    void reload() {
        UiTask.run(() -> ctx.users().listUsers(), rows -> table.getItems().setAll(rows));
    }

    @FXML
    private void onAdd() {
        nav.<UserFormController>showDialog("user-form.fxml", "Add user", c -> c.initCreate(this::reload));
    }

    @FXML
    private void onChangeRole() {
        AppUser selected = selected("Change role");
        if (selected != null) {
            nav.<UserFormController>showDialog("user-form.fxml", "Change role - " + selected.username(),
                    c -> c.initChangeRole(selected, this::reload));
        }
    }

    @FXML
    private void onResetPassword() {
        AppUser selected = selected("Reset password");
        if (selected != null) {
            nav.<UserFormController>showDialog("user-form.fxml", "Reset password - " + selected.username(),
                    c -> c.initResetPassword(selected, this::reload));
        }
    }

    @FXML
    private void onToggleActive() {
        AppUser selected = selected("Account status");
        if (selected == null) {
            return;
        }
        boolean activate = !selected.active();
        String question = activate ? "Activate " + selected.username() + "?"
                : "Deactivate " + selected.username() + "? They will no longer be able to sign in.";
        if (!AlertUtil.confirm(activate ? "Activate user" : "Deactivate user", question)) {
            return;
        }
        UiTask.runVoid(() -> ctx.users().setActive(selected.id(), activate), () -> {
            nav.toast(activate ? "User activated" : "User deactivated");
            reload();
        }, toggleButton);
    }

    private AppUser selected(String title) {
        AppUser selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.info(title, "Select a user first.");
        }
        return selected;
    }
}
