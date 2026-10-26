package com.estateflow.util;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.Map;

/**
 * Small outline icon set (24x24 grid) drawn as vector paths, so the application needs no icon font,
 * image files or extra dependency. Colour comes from the CSS class (-fx-stroke).
 */
public final class Icons {

    private static final Map<String, String> PATHS = Map.ofEntries(
            Map.entry("dashboard", "M4 4h6v7H4z M14 4h6v4h-6z M14 12h6v8h-6z M4 15h6v5H4z"),
            Map.entry("properties", "M5 21V4h9v17 M14 9h5v12 M2 21h20 M8 8h3 M8 12h3 M8 16h3 M16 13h1 M16 17h1"),
            Map.entry("owners", "M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4 21c0-4.2 3.6-7 8-7s8 2.8 8 7"),
            Map.entry("tenants", "M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z M2.5 20c0-3.5 2.9-6 6.5-6s6.5 2.5 6.5 6 M16 4.6a3.5 3.5 0 0 1 0 6.4 M18.2 14.3c2 .8 3.3 2.7 3.3 5.7"),
            Map.entry("agreements", "M6 3h8l5 5v13H6z M14 3v5h5 M9 13h7 M9 17h7"),
            Map.entry("ledger", "M4 5h16v14H4z M4 10h16 M9 10v9"),
            Map.entry("payments", "M3 6h18v12H3z M3 10h18 M7 15h4"),
            Map.entry("overdue", "M12 3.5l9.5 16.5h-19z M12 10v4.5 M12 17.2v.3"),
            Map.entry("reminders", "M6 17v-6a6 6 0 0 1 12 0v6l1.5 2h-15z M10 21a2 2 0 0 0 4 0"),
            Map.entry("expiring", "M4 6h16v15H4z M4 11h16 M8 3v5 M16 3v5 M12 14v3l2 1"),
            Map.entry("maintenance", "M14.5 6.2a4 4 0 0 0 4.8 5.1l-8.6 8.6a2.1 2.1 0 0 1-3-3l8.6-8.6a4 4 0 0 1-1.8-2.1z"),
            Map.entry("reports", "M5 20V11 M11 20V4 M17 20v-6 M3 20h18"),
            Map.entry("activity", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M12 7v5l3.5 2"),
            Map.entry("settings", "M4 7h10 M18 7h2 M4 17h2 M10 17h10 M16 5v4 M8 15v4"),
            Map.entry("search", "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z M20 20l-4-4"),
            Map.entry("plus", "M12 5v14 M5 12h14"),
            Map.entry("refresh", "M20 11a8 8 0 1 0-2.3 6.1 M20 4v7h-7"),
            Map.entry("chevron", "M9 6l6 6-6 6"),
            Map.entry("check", "M5 12.5l4.5 4.5L19 7.5"),
            Map.entry("export", "M12 3v12 M7 10l5 5 5-5 M4 20h16"),
            Map.entry("profile", "M12 12a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9z M3.5 21c.6-4.4 4-7 8.5-7s7.9 2.6 8.5 7"));

    private Icons() {
    }

    public static boolean has(String name) {
        return PATHS.containsKey(name);
    }

    /** A stroked icon of the given pixel size; style it with the CSS class "icon" (+ a colour class). */
    public static Node icon(String name, double size) {
        String content = PATHS.get(name);
        if (content == null) {
            throw new IllegalArgumentException("Unknown icon: " + name);
        }
        double scale = size / 24.0;
        SVGPath path = new SVGPath();
        path.setContent(content);
        path.getStyleClass().add("icon");
        path.setFill(null);
        path.setStrokeWidth(1.7);
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setStrokeLineJoin(StrokeLineJoin.ROUND);
        path.setScaleX(scale);
        path.setScaleY(scale);
        path.setStrokeWidth(1.7);
        Group box = new Group(path);
        box.setManaged(true);
        return box;
    }
}
