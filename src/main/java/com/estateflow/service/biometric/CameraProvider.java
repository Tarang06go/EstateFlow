package com.estateflow.service.biometric;

import java.time.Duration;

public interface CameraProvider {
    boolean available();
    CaptureSession open(Duration timeout, CancellationSignal cancellation) throws CameraUnavailableException;

    interface CaptureSession extends AutoCloseable {
        CameraFrame capture(Duration timeout, CancellationSignal cancellation) throws CameraUnavailableException;
        @Override void close();
    }
}
