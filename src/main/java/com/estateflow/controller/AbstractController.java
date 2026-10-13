package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.util.SceneNavigator;

/** Base for screen controllers: holds the injected services and navigator. */
public abstract class AbstractController {
    protected final AppContext ctx;
    protected final SceneNavigator nav;

    protected AbstractController(AppContext ctx, SceneNavigator nav) {
        this.ctx = ctx;
        this.nav = nav;
    }
}
