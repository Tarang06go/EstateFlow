package com.estateflow.ui;

import com.estateflow.config.AppContext;
import com.estateflow.controller.AssistantWidgetController;
import com.estateflow.util.SceneNavigator;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/**
 * Behavioural hit-test of the floating assistant overlay: loads the real FXML and stylesheet into a real
 * scene graph and asks which node would receive the mouse at several points. Needs a JavaFX toolkit
 * (a desktop session); it is skipped, not passed, when none is available.
 */
class AssistantOverlayHitTestTest {
    private static boolean toolkit;

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
            toolkit = true;
        } catch (IllegalStateException alreadyRunning) {
            toolkit = true;
        } catch (RuntimeException noDisplay) {
            toolkit = false;
        }
    }

    @Test
    void emptyOverlayAreaNeverInterceptsPageInput() throws Exception {
        assumeTrue(toolkit, "JavaFX toolkit unavailable (headless)");
        onFx(() -> {
            Fixture f = new Fixture();
            Bounds dock = f.dock.localToScene(f.dock.getBoundsInLocal());
            // A point in the dock's empty top-left corner: over the page, away from launcher and chat panel.
            double x = dock.getMinX() + 8, y = dock.getMinY() + 8;
            assertSame(f.page, f.pick(x, y), "closed assistant must let clicks and wheel events reach the page");

            Node launcher = f.root.lookup("#launcherButton");
            Bounds lb = launcher.localToScene(launcher.getBoundsInLocal());
            Node hit = f.pick(lb.getCenterX(), lb.getCenterY());
            assertTrue(isWithin(hit, launcher), "launcher must still receive clicks");

            // Open the chat panel: the page must still receive input above it, the panel must receive input.
            Node panel = f.root.lookup("#chatPanel");
            panel.setManaged(true);
            panel.setVisible(true);
            f.layout();
            assertSame(f.page, f.pick(x, y), "open assistant must not capture the area above its panel");
            Bounds pb = panel.localToScene(panel.getBoundsInLocal());
            assertTrue(isWithin(f.pick(pb.getCenterX(), pb.getCenterY()), panel), "open chat panel must receive input");
            return null;
        });
    }

    @Test
    void overlayStaysInsideItsCompactBoundsWhenViewportGrows() throws Exception {
        assumeTrue(toolkit, "JavaFX toolkit unavailable (headless)");
        onFx(() -> {
            Fixture f = new Fixture();
            f.resize(1800, 1100);
            Bounds dock = f.dock.localToScene(f.dock.getBoundsInLocal());
            assertTrue(dock.getWidth() <= 420 + 0.5 && dock.getHeight() <= 620 + 0.5,
                    "dock must not grow with the viewport: " + dock);
            assertSame(f.page, f.pick(10, 10), "top-left page area must belong to the page");
            assertSame(f.page, f.pick(900, 500), "centre of a large window must belong to the page");
            return null;
        });
    }

    /** Page plus the real assistant widget stacked the way main.fxml stacks them. */
    private static final class Fixture {
        final Pane page = new Pane();
        final StackPane layer = new StackPane();
        final StackPane stack = new StackPane(page, layer);
        final Parent root;
        final Node dock;

        Fixture() {
            try {
                AppContext ctx = mock(AppContext.class, Answers.RETURNS_DEEP_STUBS);
                SceneNavigator nav = mock(SceneNavigator.class);
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/assistant-widget.fxml"));
                loader.setControllerFactory(t -> new AssistantWidgetController(ctx, nav));
                root = loader.load();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            layer.setAlignment(Pos.BOTTOM_RIGHT);
            layer.setPickOnBounds(false);
            layer.getChildren().add(root);
            Scene scene = new Scene(stack, 1100, 800);
            scene.getStylesheets().add(getClass().getResource("/css/estateflow.css").toExternalForm());
            dock = root;
            layout();
        }

        void resize(double w, double h) { stack.resize(w, h); layout(); }
        void layout() { stack.applyCss(); stack.layout(); }

        /** Mirrors JavaFX picking: topmost visible, non-mouse-transparent node whose shape contains the point. */
        Node pick(double sceneX, double sceneY) {
            return pick(stack, sceneX, sceneY);
        }

        private Node pick(Node n, double x, double y) {
            if (!n.isVisible() || n.isMouseTransparent()) {
                return null;
            }

            if (n instanceof Parent p) {
                var kids = p.getChildrenUnmodifiable();

                for (int i = kids.size() - 1; i >= 0; i--) {
                    Node hit = pick(kids.get(i), x, y);

                    if (hit != null) {
                        return hit;
                    }
                }
            }

            return n.contains(n.sceneToLocal(x, y)) ? n : null;
        }
    }

    private static boolean isWithin(Node hit, Node ancestor) {
        for (Node n = hit; n != null; n = n.getParent()) if (n == ancestor) return true;
        return false;
    }

    private static <T> T onFx(Supplier<T> body) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { result.complete(body.get()); } catch (Throwable t) { result.completeExceptionally(t); }
        });
        try { return result.get(30, TimeUnit.SECONDS); }
        catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof Error err) throw err;
            throw e;
        }
    }
}
