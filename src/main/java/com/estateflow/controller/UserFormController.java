package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.exception.ValidationException;
import com.estateflow.model.AppUser;
import com.estateflow.model.enums.Role;
import com.estateflow.security.PasswordPolicy;
import com.estateflow.util.ErrorHandler;
import com.estateflow.util.SceneNavigator;
import com.estateflow.util.UiTask;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.util.StringConverter;

/** One dialog for the three account edits: create, change role, reset password. */
public class UserFormController extends AbstractDialogController {

    private enum Mode { CREATE, CHANGE_ROLE, RESET_PASSWORD }

    @FXML private Label usernameLabel, passwordLabel, confirmLabel, roleLabel, hintLabel;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField, confirmField;
    @FXML private ComboBox<Role> roleBox;
    @FXML private Button saveButton;

    private Mode mode = Mode.CREATE;
    private AppUser target;
    private Runnable onSaved = () -> { };

    public UserFormController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        roleBox.getItems().setAll(Role.values());
        roleBox.setConverter(new StringConverter<>() {
            @Override public String toString(Role r) { return r == null ? "" : r.label(); }
            @Override public Role fromString(String s) { return null; }
        });
        roleBox.setValue(Role.VIEWER);
    }

    void initCreate(Runnable onSaved) {
        this.mode = Mode.CREATE;
        this.onSaved = onSaved;
        hintLabel.setText("At least 10 characters with a letter and a digit. Viewers can read but not change anything.");
    }

    void initChangeRole(AppUser user, Runnable onSaved) {
        this.mode = Mode.CHANGE_ROLE;
        this.target = user;
        this.onSaved = onSaved;
        show(false, usernameLabel, usernameField, passwordLabel, passwordField, confirmLabel, confirmField);
        roleBox.setValue(user.role());
        hintLabel.setText("Account: " + user.username());
    }

    void initResetPassword(AppUser user, Runnable onSaved) {
        this.mode = Mode.RESET_PASSWORD;
        this.target = user;
        this.onSaved = onSaved;
        show(false, usernameLabel, usernameField, roleLabel, roleBox);
        passwordLabel.setText("New password *");
        hintLabel.setText("Account: " + user.username() + ". The old password is not shown or recovered; "
                + "give the new one to the user securely.");
    }

    private static void show(boolean visible, Node... nodes) {
        for (Node n : nodes) {
            n.setVisible(visible);
            n.setManaged(visible);
        }
    }

    @FXML
    private void onSave() {
        try {
            switch (mode) {
                case CREATE -> create();
                case CHANGE_ROLE -> changeRole();
                case RESET_PASSWORD -> resetPassword();
            }
        } catch (EstateFlowException e) {
            ErrorHandler.handle(e);
        }
    }

    private void create() throws ValidationException {
        String username = usernameField.getText();
        String password = passwordField.getText();
        requireMatch(password);
        Role role = roleBox.getValue();
        UiTask.run(() -> ctx.users().createUser(username, password, role), user -> finish("User created"), saveButton);
    }

    private void changeRole() {
        Role role = roleBox.getValue();
        UiTask.runVoid(() -> ctx.users().changeRole(target.id(), role), () -> finish("Role updated"), saveButton);
    }

    private void resetPassword() throws ValidationException {
        String password = passwordField.getText();
        requireMatch(password);
        PasswordPolicy.validatePassword(password, target.username());
        UiTask.runVoid(() -> ctx.users().resetPassword(target.id(), password), () -> finish("Password reset"), saveButton);
    }

    private void requireMatch(String password) throws ValidationException {
        if (password == null || !password.equals(confirmField.getText())) {
            throw new ValidationException("The two passwords do not match.");
        }
    }

    private void finish(String message) {
        passwordField.clear();
        confirmField.clear();
        close();
        nav.toast(message);
        onSaved.run();
    }

    @FXML
    private void onCancel() {
        close();
    }
}
