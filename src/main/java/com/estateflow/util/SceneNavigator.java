package com.estateflow.util;

import com.estateflow.config.AppContext;
import com.estateflow.exception.ConfigurationException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

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
    private StackPane contentArea;

    public SceneNavigator(AppContext context, Stage primaryStage) {
        this.context = context;
        this.primaryStage = primaryStage;
    }

    public void setContentArea(StackPane contentArea) {
        this.contentArea = contentArea;
    }

    public AppContext context() {
        return context;
    }

    /** Loads an FXML file; its controller is created with (AppContext, SceneNavigator). */
    public <C> Loaded<C> load(String fxmlName) {
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
            return new Loaded<>(root, loader.getController());
        } catch (IOException e) {
            throw new ConfigurationException("Cannot load screen " + fxmlName + ": " + e.getMessage(), e);
        }
    }

    /** Shows a screen in the content area and returns its controller for further setup. */
    public <C> C show(String fxmlName) {
        Loaded<C> loaded = load(fxmlName);
        contentArea.getChildren().setAll(loaded.root());
        return loaded.controller();
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
