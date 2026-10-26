package com.estateflow.service.biometric;

import java.util.concurrent.atomic.AtomicBoolean;

/** Owns one in-memory camera frame. It is never serialized or written to disk. */
public final class CameraFrame implements AutoCloseable {
    private final Object image;
    private final AutoCloseable release;
    private final AtomicBoolean closed = new AtomicBoolean();

    public CameraFrame(Object image, AutoCloseable release) {
        this.image = image;
        this.release = release;
    }

    Object image() {
        if (closed.get()) throw new IllegalStateException("Camera frame is closed.");
        return image;
    }

    @Override public void close() throws Exception {
        if (!closed.compareAndSet(false, true)) return;
        release.close();
    }
}
