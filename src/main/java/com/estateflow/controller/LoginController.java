package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.util.ErrorHandler;
import com.estateflow.util.LogoMark;
import com.estateflow.util.SceneNavigator;
import com.estateflow.util.UiTask;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.concurrent.Task;

/**
 * Sign-in screen. When no active administrator exists it switches to first-run setup, where the person
 * installing EstateFlow chooses the first administrator's username and password (nothing is preset).
 */
public class LoginController extends AbstractController {

    @FXML private StackPane brandHolder;
    @FXML private Label titleLabel, subtitleLabel, errorLabel, hintLabel;
    @FXML private TextField usernameField, passwordText;
    @FXML private PasswordField passwordField, confirmField;
    @FXML private VBox confirmBox;
    @FXML private CheckBox showPassword;
    @FXML private Button submitButton, faceLoginButton, cancelFaceButton;
    @FXML private Label faceStatusLabel;

    private boolean ready;
    private boolean setupMode;
    private boolean faceProviderAvailable;
    private Task<com.estateflow.model.AppUser> faceTask;

    public LoginController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        brandHolder.getChildren().add(LogoMark.lockup(40));
        passwordText.textProperty().bindBidirectional(passwordField.textProperty());
        showPassword.selectedProperty().addListener((o, was, show) -> {
            passwordText.setVisible(show);
            passwordText.setManaged(show);
            passwordField.setVisible(!show);
            passwordField.setManaged(!show);
        });
        checkDatabase();
    }

    /** Verifies the user table exists and decides between sign-in and first-run setup. */
    private void checkDatabase() {
        setFormDisabled(true);
        showError(null);
        subtitleLabel.setText("Checking the database...");
        UiTask.run(() -> {
            ctx.auth().ensureReady();
            boolean needsSetup = ctx.auth().needsInitialSetup();
            boolean faceLoginEnabled = ctx.config().faceLoginEnabled();
            boolean faceAvailable = !needsSetup && ctx.facialVerification().loginProviderAvailable();
            return new LoginReadiness(needsSetup, faceAvailable, faceLoginEnabled);
        }, readiness -> {
            ready = true;
            configure(readiness.needsSetup());
            faceProviderAvailable = readiness.faceAvailable();
            faceLoginButton.setDisable(!readiness.faceAvailable());
            faceStatusLabel.setText(readiness.needsSetup() ? "Create an administrator password to get started." :
                    !readiness.faceLoginEnabled() ? "Face sign-in is disabled by the application safety setting. Password sign-in remains available." :
                    readiness.faceAvailable() ? "Optional convenience sign-in. Photo or video replay may fool face matching; no liveness check is used." :
                    "Face sign-in is unavailable here. Use your password.");
            setFormDisabled(false);
            usernameField.requestFocus();
        }, failure -> {
            ready = false;
            subtitleLabel.setText("EstateFlow cannot start sign-in yet.");
            showError("The database is not ready. See the message shown, fix it, then press Retry.");
            submitButton.setText("Retry");
            submitButton.setDisable(false);
            ErrorHandler.handle(failure);
        });
    }

    private record LoginReadiness(boolean needsSetup, boolean faceAvailable, boolean faceLoginEnabled) { }

    private void configure(boolean needsSetup) {
        setupMode = needsSetup;
        confirmBox.setVisible(needsSetup);
        confirmBox.setManaged(needsSetup);
        if (needsSetup) {
            titleLabel.setText("Set up EstateFlow");
            subtitleLabel.setText("No administrator exists yet. Create the first administrator account.");
            submitButton.setText("Create administrator");
            hintLabel.setText("Use at least 10 characters with a letter and a digit. Keep this password safe: "
                    + "it cannot be recovered, only reset by another administrator.");
        } else {
            titleLabel.setText("Sign in");
            subtitleLabel.setText("Welcome back. Enter your account details to continue.");
            submitButton.setText("Sign in");
            hintLabel.setText("");
        }
    }

    @FXML
    private void onSubmit() {
        if (!ready) {
            checkDatabase();
            return;
        }
        String username = usernameField.getText();
        String password = passwordField.getText();
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            showError("Enter your username and password.");
            return;
        }
        if (setupMode && !password.equals(confirmField.getText())) {
            showError("The two passwords do not match.");
            return;
        }
        showError(null);
        String idleText = submitButton.getText();
        submitButton.setText(setupMode ? "Creating..." : "Signing in...");
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(16, 16);
        submitButton.setGraphic(spinner);
        boolean setup = setupMode;
        UiTask.run(() -> {
            if (setup) {
                ctx.auth().createInitialAdministrator(username, password);
            }
            return ctx.auth().login(username, password);
        }, user -> {
            clearSecrets();
            nav.enterApplication();
        }, failure -> {
            submitButton.setText(idleText);
            submitButton.setGraphic(null);
            clearSecrets();
            if (failure instanceof EstateFlowException e) {
                showError(e.getMessage());
            } else {
                ErrorHandler.handle(failure);
            }
        }, submitButton, usernameField, passwordField, passwordText, confirmField, showPassword);
    }

    @FXML private void onFaceLogin() {
        if (!ready || setupMode) return;
        String username = usernameField.getText();
        if (username == null || username.isBlank()) { showError("Enter your username, then choose face sign-in."); return; }
        showError(null);
        faceStatusLabel.setText("Opening the camera. You can cancel or use your password instead.");
        faceTask = UiTask.runCancellable(() -> {
            var proof = ctx.facialVerification().verifyForLogin(username, () -> faceTask != null && faceTask.isCancelled());
            if (Thread.currentThread().isInterrupted() || (faceTask != null && faceTask.isCancelled()))
                throw new com.estateflow.exception.AuthenticationException("Face sign-in was cancelled.");
            return ctx.auth().loginWithFace(proof);
        }, user -> { faceTask = null; clearSecrets(); nav.enterApplication(); }, failure -> {
            faceTask = null;
            if (failure instanceof EstateFlowException e) showError(e.getMessage());
            else { showError("Face sign-in is unavailable. Use your password."); ErrorHandler.handle(failure); }
            faceStatusLabel.setText("Face sign-in ended. Password sign-in remains available.");
        }, () -> { faceTask = null; faceStatusLabel.setText("Face sign-in cancelled. Password sign-in remains available."); },
                usernameField, faceLoginButton, submitButton);
    }

    @FXML private void onCancelFaceLogin() {
        if (faceTask != null) faceTask.cancel(true);
    }

    private void clearSecrets() {
        passwordField.clear();
        confirmField.clear();
    }

    private void setFormDisabled(boolean disabled) {
        for (Node n : new Node[]{submitButton, faceLoginButton, usernameField, passwordField, passwordText, confirmField, showPassword}) {
            n.setDisable(disabled || (n == faceLoginButton && (!faceProviderAvailable || setupMode)));
        }
    }

    private void showError(String message) {
        boolean show = message != null && !message.isBlank();
        errorLabel.setText(show ? message : "");
        errorLabel.setVisible(show);
        errorLabel.setManaged(show);
    }
}
