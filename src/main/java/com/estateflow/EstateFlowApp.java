package com.estateflow;

import com.estateflow.config.AppConfig;
import com.estateflow.config.AppContext;
import com.estateflow.util.ErrorHandler;
import com.estateflow.util.SceneNavigator;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** JavaFX application: startup, global exception handling, and the main window. */
public class EstateFlowApp extends Application {
    private AppContext context;

    @Override
    public void start(Stage stage) {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> ErrorHandler.handle(error));
        loadBundledFonts();
        try {
            AppConfig config = AppConfig.load();
            context = new AppContext(config);
            Scene scene = new Scene(new javafx.scene.layout.StackPane(), 1180, 740);
            scene.getStylesheets().add(getClass().getResource("/css/estateflow.css").toExternalForm());
            for (String size : new String[]{"16", "32", "64", "256"}) {
                var icon = getClass().getResourceAsStream("/images/app-icon-" + size + ".png");
                if (icon != null) {
                    stage.getIcons().add(new javafx.scene.image.Image(icon));
                }
            }
            stage.setTitle("EstateFlow - Property & Rental Management");
            stage.setMinWidth(1040);
            stage.setMinHeight(680);
            stage.setScene(scene);
            showScreen(stage, scene, context, "login.fxml");
            stage.show();
        } catch (RuntimeException startupFailure) {
            ErrorHandler.handle(startupFailure); // startup path: the single place that reports and logs it
            Platform.exit();
        }
    }

    @Override public void stop() {
        if (context != null) context.facialVerification().close();
    }

    /** Swaps the window content between sign-in and the shell; each switch builds a fresh navigator and screens. */
    private void showScreen(Stage stage, Scene scene, AppContext context, String fxml) {
        SceneNavigator navigator = new SceneNavigator(context, stage);
        navigator.setShellSwitcher(new SceneNavigator.ShellSwitcher() {
            @Override
            public void showMain() {
                showScreen(stage, scene, context, "main.fxml");
            }

            @Override
            public void showLogin() {
                showScreen(stage, scene, context, "login.fxml");
            }
        });
        SceneNavigator.Loaded<Object> loaded = navigator.load(fxml);
        scene.setRoot(loaded.root());
    }

    /** Bundled Inter keeps the look identical on every machine; the CSS falls back to system fonts if loading fails. */
    private void loadBundledFonts() {
        for (String file : new String[]{"Inter-Regular.otf", "Inter-Bold.otf"}) {
            var url = getClass().getResource("/fonts/" + file);
            if (url != null) {
                javafx.scene.text.Font.loadFont(url.toExternalForm(), 13);
            }
        }
    }
}
