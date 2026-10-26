package com.estateflow.service.biometric;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.global.opencv_videoio;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.bytedeco.opencv.global.opencv_videoio.*;

/** Windows DirectShow camera access. Each operation owns and releases its capture session. */
public final class OpenCvCameraProvider implements CameraProvider {
    @Override public boolean available() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) return false;
        try { Loader.load(opencv_videoio.class); return true; }
        catch (Throwable unavailable) { return false; }
    }

    @Override public CaptureSession open(Duration timeout, CancellationSignal cancellation) throws CameraUnavailableException {
        if (cancellation.cancelled()) throw new CameraUnavailableException("Camera operation was cancelled.");
        if (!available()) throw new CameraUnavailableException("Windows camera support is unavailable.");
        VideoCapture capture = new VideoCapture();
        boolean opened = false;
        try {
            opened = capture.open(0, CAP_DSHOW) && capture.isOpened();

            if (opened) {
                capture.set(CAP_PROP_FRAME_WIDTH, 640);
                capture.set(CAP_PROP_FRAME_HEIGHT, 480);
            }
            if (!opened || cancellation.cancelled()) throw new CameraUnavailableException("No usable camera was found.");
            return new Session(capture);
        } catch (CameraUnavailableException e) {
            closeCapture(capture); throw e;
        } catch (Throwable e) {
            closeCapture(capture);
            throw new CameraUnavailableException("The camera could not be opened.", e);
        }
    }

    private static void closeCapture(VideoCapture capture) {
        try { capture.release(); } finally { capture.close(); }
    }

    private static final class Session implements CaptureSession {
        private final VideoCapture capture;
        private boolean closed;
        private Session(VideoCapture capture) { this.capture = capture; }
        @Override public synchronized CameraFrame capture(Duration timeout, CancellationSignal cancellation) throws CameraUnavailableException {
            if (closed || cancellation.cancelled()) throw new CameraUnavailableException("Camera operation was cancelled.");
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                if (cancellation.cancelled() || Thread.currentThread().isInterrupted()) throw new CameraUnavailableException("Camera operation was cancelled.");
                Mat frame = new Mat();
                try {
                    if (capture.read(frame) && !frame.empty()) {
                        return new CameraFrame(frame, frame::close);
                    }
                    frame.close();
                    TimeUnit.MILLISECONDS.sleep(40);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); frame.close(); throw new CameraUnavailableException("Camera operation was cancelled.", e);
                } catch (Throwable e) {
                    frame.close(); throw new CameraUnavailableException("The camera did not provide a usable frame.", e);
                }
            }
            throw new CameraUnavailableException("The camera did not provide a frame before the timeout.");
        }
        @Override public synchronized void close() {
            if (closed) return;
            closed = true; closeCapture(capture);
        }
    }
}
