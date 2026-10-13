package com.estateflow;

import javafx.application.Application;

/** Non-JavaFX entry point so the application also starts from a plain classpath. */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(EstateFlowApp.class, args);
    }
}
