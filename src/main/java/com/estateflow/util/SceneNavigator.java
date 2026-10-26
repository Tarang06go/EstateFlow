package com.estateflow.util;

import com.estateflow.config.AppContext;
import com.estateflow.exception.ConfigurationException;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;

/**
 * Loads FXML with a controller factory that injects the composition root, swaps screens into the
 * main content area, and opens modal dialogs.
 */
public final class SceneNavigator {

    private final AppContext context;
    private final Stage primaryStage;
    private ScrollPane contentArea;
    private VBox toastBox;
    private Consumer<String> screenListener = name -> { };
    private ShellSwitcher shell;

    /** Screens reachable before sign-in. Everything else requires an authenticated session. */
    private static final java.util.Set<String> PUBLIC_SCREENS = java.util.Set.of("login.fxml");
    /** Screens that need more than a signed-in user. */
    private static final java.util.Map<String, com.estateflow.security.Permission> SCREEN_PERMISSIONS = java.util.Map.ofEntries(
            java.util.Map.entry("users.fxml", com.estateflow.security.Permission.MANAGE_USERS),
            java.util.Map.entry("user-form.fxml", com.estateflow.security.Permission.MANAGE_USERS),
            java.util.Map.entry("audit.fxml", com.estateflow.security.Permission.VIEW_AUDIT),
            java.util.Map.entry("reports.fxml", com.estateflow.security.Permission.VIEW_REPORTS),
            // Write dialogs: second layer behind the hidden buttons (services remain the real enforcement).
            java.util.Map.entry("owner-form.fxml", com.estateflow.security.Permission.MANAGE_RECORDS),
            java.util.Map.entry("tenant-form.fxml", com.estateflow.security.Permission.MANAGE_RECORDS),
            java.util.Map.entry("property-form.fxml", com.estateflow.security.Permission.MANAGE_RECORDS),
            java.util.Map.entry("agreement-form.fxml", com.estateflow.security.Permission.MANAGE_RECORDS),
            java.util.Map.entry("maintenance-form.fxml", com.estateflow.security.Permission.MANAGE_RECORDS),
            java.util.Map.entry("deposit-form.fxml", com.estateflow.security.Permission.RECORD_PAYMENTS),
            java.util.Map.entry("deposit-void.fxml", com.estateflow.security.Permission.RECORD_PAYMENTS),
            java.util.Map.entry("lease-renew.fxml", com.estateflow.security.Permission.MANAGE_RECORDS),
            java.util.Map.entry("expense-form.fxml", com.estateflow.security.Permission.MANAGE_RECORDS),
            java.util.Map.entry("delete-expense.fxml", com.estateflow.security.Permission.MANAGE_RECORDS),
            java.util.Map.entry("profitability.fxml", com.estateflow.security.Permission.VIEW_REPORTS),
            java.util.Map.entry("analytics.fxml", com.estateflow.security.Permission.VIEW_REPORTS),
            java.util.Map.entry("assistant.fxml", com.estateflow.security.Permission.VIEW_REPORTS),
            java.util.Map.entry("document-form.fxml", com.estateflow.security.Permission.MANAGE_RECORDS),
            java.util.Map.entry("inspection-form.fxml", com.estateflow.security.Permission.MANAGE_RECORDS),
            java.util.Map.entry("vendor-form.fxml", com.estateflow.security.Permission.MANAGE_RECORDS),
            java.util.Map.entry("work-order-form.fxml", com.estateflow.security.Permission.MANAGE_RECORDS),
            java.util.Map.entry("payment-form.fxml", com.estateflow.security.Permission.RECORD_PAYMENTS),
            java.util.Map.entry("void-payment.fxml", com.estateflow.security.Permission.RECORD_PAYMENTS));

    public SceneNavigator(AppContext context, Stage primaryStage) {
        this.context = context;
        this.primaryStage = primaryStage;
    }

    /** Switches the window between the sign-in screen and the application shell. */
    public interface ShellSwitcher {
        void showMain();

        void showLogin();
    }

    public void setShellSwitcher(ShellSwitcher shell) {
        this.shell = shell;
    }

    /** Called by the sign-in screen after a successful login. */
    public void enterApplication() {
        context.authorizer().requireAuthenticated();
        shell.showMain();
    }

    /** Clears the session and returns to the sign-in screen. */
    public void signOut() {
        context.auth().logout();
        shell.showLogin();
    }

    public void setContentArea(ScrollPane contentArea) {
        this.contentArea = contentArea;
        this.contentArea.setFitToWidth(true);
        this.contentArea.setFitToHeight(false);
        this.contentArea.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        this.contentArea.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    }

    /** Receives the FXML file name every time a screen is shown (the shell uses it to mark the active menu item). */
    public void setScreenListener(Consumer<String> listener) {
        this.screenListener = listener;
    }

    public void setToastBox(VBox toastBox) {
        this.toastBox = toastBox;
    }

    /** Short confirmation message in the bottom-right corner; disappears by itself. */
    public void toast(String message) {
        if (toastBox == null) {
            return;
        }
        Label label = new Label(message);
        label.getStyleClass().add("toast");
        toastBox.getChildren().add(label);
        PauseTransition wait = new PauseTransition(Duration.seconds(2.6));
        wait.setOnFinished(e -> {
            FadeTransition fade = new FadeTransition(Duration.millis(350), label);
            fade.setToValue(0);
            fade.setOnFinished(f -> toastBox.getChildren().remove(label));
            fade.play();
        });
        wait.play();
    }

    public AppContext context() {
        return context;
    }

    /** Loads an FXML file; its controller is created with (AppContext, SceneNavigator). */
    public <C> Loaded<C> load(String fxmlName) {
        guard(fxmlName);
        URL url = SceneNavigator.class.getResource("/fxml/" + fxmlName);
        if (url == null) {
            throw new ConfigurationException("Screen definition not found: /fxml/" + fxmlName);
        }
        FXMLLoader loader = new FXMLLoader(url);
        loader.setControllerFactory(type -> {
            try {
                return type.getConstructor(AppContext.class, SceneNavigator.class).newInstance(context, this);
            } catch (ReflectiveOperationException e) {
                throw new ConfigurationException("Cannot create controller " + type.getName(), e);
            }
        });
        try {
            Parent root = loader.load();
            AccessUi.apply(root, context.authorizer());
            return new Loaded<>(root, loader.getController());
        } catch (IOException e) {
            throw new ConfigurationException("Cannot load screen " + fxmlName + ": " + e.getMessage(), e);
        }
    }

    /** Unauthenticated users get nothing but the sign-in screen; restricted screens check the role. */
    private void guard(String fxmlName) {
        if (PUBLIC_SCREENS.contains(fxmlName)) {
            return;
        }
        context.authorizer().requireAuthenticated();
        var needed = SCREEN_PERMISSIONS.get(fxmlName);
        if (needed != null) {
            context.authorizer().require(needed);
        }
    }

    /** Shows a screen in the content area and returns its controller for further setup. */
    public <C> C show(String fxmlName) {
        Loaded<C> loaded = load(fxmlName);
        Node page = unwrapPageScrollPane(loaded.root());
        // Preserve the page's natural vertical layout size. The viewport controls width,
        // while the page keeps its preferred height and can therefore extend below it.
        if (page instanceof Region region) {
            region.setMinHeight(Region.USE_PREF_SIZE);
        }
        contentArea.setContent(page);
        contentArea.setVvalue(0);
        screenListener.accept(fxmlName);
        return loaded.controller();
    }

    /** Page FXMLs may provide a root ScrollPane; put its content into the shell's single viewport. */
    static Node unwrapPageScrollPane(Node page) {
        if (page instanceof ScrollPane pageScroll && pageScroll.getContent() != null) {
            return pageScroll.getContent();
        }
        return page;
    }

    /** Opens a modal dialog; the init callback receives the controller before the dialog is shown. */
    public <C> void showDialog(String fxmlName, String title, Consumer<C> init) {
        Loaded<C> loaded = load(fxmlName);
        Stage dialog = new Stage();
        dialog.initOwner(primaryStage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(title);
        Scene scene = new Scene(loaded.root());
        scene.getStylesheets().add(SceneNavigator.class.getResource("/css/estateflow.css").toExternalForm());
        dialog.setScene(scene);
        if (loaded.controller() instanceof DialogController dc) {
            dc.setStage(dialog);
        }
        init.accept(loaded.controller());
        dialog.showAndWait();
    }

    public Stage primaryStage() {
        return primaryStage;
    }

    public record Loaded<C>(Parent root, C controller) {
    }

    /** Implemented by dialog controllers so they can close themselves. */
    public interface DialogController {
        void setStage(Stage stage);
    }
}
