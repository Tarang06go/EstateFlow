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

    @Override
    public void start(Stage stage) {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> ErrorHandler.handle(error));
        try {
            AppConfig config = AppConfig.load();
            AppContext context = new AppContext(config);
            SceneNavigator navigator = new SceneNavigator(context, stage);
            SceneNavigator.Loaded<Object> main = navigator.load("main.fxml");
            Scene scene = new Scene(main.root(), 1180, 740);
            scene.getStylesheets().add(getClass().getResource("/css/estateflow.css").toExternalForm());
            stage.setTitle("EstateFlow - Property & Rental Management");
            stage.setMinWidth(1040);
            stage.setMinHeight(680);
            stage.setScene(scene);
            stage.show();
        } catch (RuntimeException startupFailure) {
            ErrorHandler.handle(startupFailure); // startup path: the single place that reports and logs it
            Platform.exit();
        }
    }
}
