package com.estateflow.util;

import javafx.scene.Node;
import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;

class SceneNavigatorLayoutTest {
    @BeforeAll
    static void startJavaFxToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        if (!started.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX toolkit did not start");
        }
    }

    @Test
    void unwrapsPageRootScrollPaneIntoTheSharedShellViewport() throws Exception {
        assertOnFxThread(() -> {
            Node content = new VBox();
            ScrollPane pageRoot = new ScrollPane(content);
            assertSame(content, SceneNavigator.unwrapPageScrollPane(pageRoot));
        });
    }

    @Test
    void leavesPagesWithoutARootScrollPaneUntouched() throws Exception {
        assertOnFxThread(() -> {
            Node page = new VBox();
            assertSame(page, SceneNavigator.unwrapPageScrollPane(page));
        });
    }

    private static void assertOnFxThread(Runnable assertion) throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                assertion.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                complete.countDown();
            }
        });
        if (!complete.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX assertion did not complete");
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }
}
