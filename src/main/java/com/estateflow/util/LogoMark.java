package com.estateflow.util;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.geometry.Pos;

/**
 * The EstateFlow mark: a deep-green rounded tile holding a simple building with a lime roof line.
 * Geometry is on a 48-unit grid and scaled, so the same drawing works at 16 px and at 256 px.
 */
public final class LogoMark {

    public static final Color FOREST = Color.web("#173F32");
    public static final Color GREEN = Color.web("#245B45");
    public static final Color LIME = Color.web("#91DC69");

    private LogoMark() {
    }

    /** The square mark only. */
    public static Node mark(double size) {
        double s = size / 48.0;
        Rectangle tile = new Rectangle(0, 0, 48, 48);
        tile.setArcWidth(22);
        tile.setArcHeight(22);
        tile.setFill(GREEN);

        Polygon roof = new Polygon(8, 23, 24, 9, 40, 23, 40, 27, 24, 14, 8, 27);
        roof.setFill(LIME);

        Rectangle body = new Rectangle(13, 26, 22, 13);
        body.setArcWidth(3);
        body.setArcHeight(3);
        body.setFill(Color.WHITE);

        Rectangle door = new Rectangle(21, 31, 6, 8);
        door.setFill(LIME);
        Rectangle windowLeft = new Rectangle(16, 29, 3, 3);
        windowLeft.setFill(GREEN);
        Rectangle windowRight = new Rectangle(29, 29, 3, 3);
        windowRight.setFill(GREEN);

        Group art = new Group(tile, roof, body, door, windowLeft, windowRight);
        art.setScaleX(s);
        art.setScaleY(s);
        Group box = new Group(art);
        return box;
    }

    /** Mark plus the "EstateFlow" wordmark (CSS classes brand-name / brand-flow). */
    public static Node lockup(double markSize) {
        Label estate = new Label("Estate");
        estate.getStyleClass().add("brand-name");
        Label flow = new Label("Flow");
        flow.getStyleClass().addAll("brand-name", "brand-flow");
        HBox word = new HBox(0, estate, flow);
        word.setAlignment(Pos.CENTER_LEFT);
        HBox box = new HBox(10, mark(markSize), word);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
