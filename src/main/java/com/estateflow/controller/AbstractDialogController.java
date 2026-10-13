package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.util.SceneNavigator;
import javafx.stage.Stage;

/** Base for modal dialog controllers. */
public abstract class AbstractDialogController extends AbstractController implements SceneNavigator.DialogController {
    private Stage stage;

    protected AbstractDialogController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    protected void close() {
        if (stage != null) {
            stage.close();
        }
    }
}
