package com.estateflow.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;

/** Small wrappers around JavaFX dialogs; always run on the JavaFX Application Thread. */
public final class AlertUtil {

    private AlertUtil() {
    }

    private static void style(Alert alert) {
        alert.getDialogPane().getStylesheets().add(AlertUtil.class.getResource("/css/estateflow.css").toExternalForm());
        alert.getDialogPane().setMinWidth(420);
        for (ButtonType type : alert.getButtonTypes()) {
            javafx.scene.control.Button button = (javafx.scene.control.Button) alert.getDialogPane().lookupButton(type);
            if (button != null && (type == ButtonType.OK)) {
                button.getStyleClass().add("btn-primary");
            }
        }
    }

    public static void info(String title, String message) {
        show(Alert.AlertType.INFORMATION, title, message);
    }

    public static void warning(String title, String message) {
        show(Alert.AlertType.WARNING, title, message);
    }

    public static void error(String title, String message) {
        show(Alert.AlertType.ERROR, title, message);
    }

    public static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(title);
        alert.setHeaderText(null);
        style(alert);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private static void show(Alert.AlertType type, String title, String message) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(type, title, message));
            return;
        }
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        style(alert);
        alert.showAndWait();
    }
}
