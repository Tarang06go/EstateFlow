package com.estateflow.util;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Node;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Runs service calls off the JavaFX Application Thread. Success and failure callbacks run on the FX
 * thread; failures go to {@link ErrorHandler}. Nodes passed in are disabled while the task runs, which
 * guards against duplicate Save clicks (a UI backstop; database locks remain the real protection).
 */
public final class UiTask {

    private static final ExecutorService POOL = Executors.newCachedThreadPool(runnable -> {
        Thread t = new Thread(runnable, "estateflow-worker");
        t.setDaemon(true);
        return t;
    });

    private static final javafx.beans.property.IntegerProperty PENDING = new javafx.beans.property.SimpleIntegerProperty(0);

    private UiTask() {
    }

    /** Number of background tasks currently running; the shell shows a thin progress bar while it is above zero. */
    public static javafx.beans.property.ReadOnlyIntegerProperty pendingProperty() {
        return PENDING;
    }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Node... disableWhileRunning) {
        run(work, onSuccess, ErrorHandler::handle, disableWhileRunning);
    }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure,
                               Node... disableWhileRunning) {
        for (Node n : disableWhileRunning) {
            n.setDisable(true);
        }
        PENDING.set(PENDING.get() + 1);
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> {
            PENDING.set(PENDING.get() - 1);
            enable(disableWhileRunning);
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(e -> {
            PENDING.set(PENDING.get() - 1);
            enable(disableWhileRunning);
            onFailure.accept(task.getException());
        });
        POOL.execute(task);
    }

    /** Background operation with a caller-visible cancellation handle for camera and native work. */
    public static <T> Task<T> runCancellable(Callable<T> work, Consumer<T> onSuccess,
                                              Consumer<Throwable> onFailure, Runnable onCancelled,
                                              Node... disableWhileRunning) {
        for (Node n : disableWhileRunning) n.setDisable(true);
        PENDING.set(PENDING.get() + 1);
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        task.setOnSucceeded(e -> { PENDING.set(PENDING.get()-1); enable(disableWhileRunning); onSuccess.accept(task.getValue()); });
        task.setOnFailed(e -> { PENDING.set(PENDING.get()-1); enable(disableWhileRunning); onFailure.accept(task.getException()); });
        task.setOnCancelled(e -> { PENDING.set(PENDING.get()-1); enable(disableWhileRunning); onCancelled.run(); });
        POOL.execute(task);
        return task;
    }

    /** For service calls without a result. */
    public static void runVoid(VoidCall work, Runnable onSuccess, Node... disableWhileRunning) {
        run(() -> {
            work.call();
            return Boolean.TRUE;
        }, ok -> onSuccess.run(), disableWhileRunning);
    }

    private static void enable(Node[] nodes) {
        for (Node n : nodes) {
            n.setDisable(false);
        }
    }

    @FunctionalInterface
    public interface VoidCall {
        void call() throws Exception;
    }

    /** Queues a runnable on the FX thread (used by the global handler). */
    public static void fx(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }
}
