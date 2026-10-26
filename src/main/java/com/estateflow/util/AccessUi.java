package com.estateflow.util;

import com.estateflow.security.Authorizer;
import com.estateflow.security.Permission;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;

/**
 * Hides controls the signed-in role may not use. A node opts in with {@code userData="<PERMISSION_NAME>"} in FXML.
 * This is a usability layer only; the real enforcement is in the services.
 */
public final class AccessUi {

    private AccessUi() {
    }

    public static void apply(Node root, Authorizer auth) {
        walk(root, auth);
    }

    private static void walk(Node node, Authorizer auth) {
        if (node == null) {
            return;
        }
        if (node.getUserData() instanceof String name) {
            try {
                if (!auth.can(Permission.valueOf(name))) {
                    node.setVisible(false);
                    node.setManaged(false);
                    return;
                }
            } catch (IllegalArgumentException ignored) {
                // userData used for something else
            }
        }
        if (node instanceof ScrollPane sp) {
            walk(sp.getContent(), auth);
        } else if (node instanceof TabPane tp) {
            tp.getTabs().forEach(t -> walk(t.getContent(), auth));
        } else if (node instanceof SplitPane sp) {
            sp.getItems().forEach(n -> walk(n, auth));
        } else if (node instanceof TitledPane tp) {
            walk(tp.getContent(), auth);
        } else if (node instanceof ToolBar tb) {
            tb.getItems().forEach(n -> walk(n, auth));
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                walk(child, auth);
            }
        }
    }
}
