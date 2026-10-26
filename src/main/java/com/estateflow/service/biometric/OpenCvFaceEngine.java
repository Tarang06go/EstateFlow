package com.estateflow.service.biometric;

import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_dnn;
import org.bytedeco.opencv.global.opencv_objdetect;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_objdetect.FaceDetectorYN;
import org.bytedeco.opencv.opencv_objdetect.FaceRecognizerSF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.bytedeco.opencv.global.opencv_core.mean;

/** Local YuNet detection and SFace feature extraction. A feature is never logged or persisted in plaintext. */
public final class OpenCvFaceEngine implements FaceEngine {
    private static final Logger log = LoggerFactory.getLogger(OpenCvFaceEngine.class);
    private final FaceModelFiles files;
    private final float scoreThreshold;
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicLong sampleSequence = new java.util.concurrent.atomic.AtomicLong();
    private volatile FaceDetectorYN detector;
    private volatile FaceRecognizerSF recognizer;
    private boolean disposed;
    public OpenCvFaceEngine(FaceModelFiles files) { this(files, .70f); }
    public OpenCvFaceEngine(FaceModelFiles files, float scoreThreshold) {
        if (!Float.isFinite(scoreThreshold) || scoreThreshold < .50f || scoreThreshold > .90f)
            throw new IllegalArgumentException("YuNet score threshold must be between 0.50 and 0.90.");
        this.files = files;
        this.scoreThreshold = scoreThreshold;
    }

    @Override public boolean available() {
        try { initialize(); return true; } catch (FaceProcessingException e) { return false; }
    }

    private synchronized void initialize() throws FaceProcessingException {
        if(disposed) throw new FaceProcessingException(FaceProcessingException.Reason.MODEL,"Face engine is closed.");
        if (initialized.get()) return;
        try {
            Loader.load(opencv_objdetect.class);
            try(Size input=new Size(640,480)) {
                detector = FaceDetectorYN.create(files.yunet().toString(), "", input, scoreThreshold, .30f, 5000,
                        opencv_dnn.DNN_BACKEND_OPENCV, opencv_dnn.DNN_TARGET_CPU);
            }
            recognizer = FaceRecognizerSF.create(files.sface().toString(), "");
            if (detector == null || detector.isNull() || recognizer == null || recognizer.isNull()) throw new IOException("OpenCV did not initialize the face models.");
            initialized.set(true);
        } catch (Throwable e) {
            if(detector!=null) { try { detector.close(); } catch(Throwable ignored) { } detector=null; }
            if(recognizer!=null) { try { recognizer.close(); } catch(Throwable ignored) { } recognizer=null; }
            log.error("Face model initialization failed: {}", e.toString());
            throw new FaceProcessingException(FaceProcessingException.Reason.MODEL,"Face models are unavailable or invalid.", e);
        }
    }

    @Override
    public synchronized FaceEmbedding extract(CameraFrame frame) throws FaceProcessingException {
        initialize();
        long sample = sampleSequence.incrementAndGet();

        Object raw = frame.image();
        if (!(raw instanceof Mat image) || image.empty()
                || image.cols() < 320 || image.rows() < 240 || image.channels() != 3
                || image.depth() != opencv_core.CV_8U) {
            log.warn("Face sample {} rejected: invalid frame dimensions/channels/depth", sample);
            throw new FaceProcessingException(FaceProcessingException.Reason.INVALID_FRAME,"The camera frame must be a usable 8-bit, three-channel BGR image at least 320x240.");
        }

        Mat detections = new Mat();
        Mat aligned = new Mat();
        Mat feature = new Mat();

        try {
            try (Scalar light = mean(image)) {
                double luminance = .114 * light.get(0)
                        + .587 * light.get(1)
                        + .299 * light.get(2);

                if (luminance < 35 || luminance > 225) {
                    log.info("Face sample {} lighting rejected: dimensions={}x{}, channels=3", sample, image.cols(), image.rows());
                    throw new FaceProcessingException(FaceProcessingException.Reason.LIGHTING,"Lighting is too dark or too bright.");
                }
            }

            try (Size input = new Size(image.cols(), image.rows())) {
                detector.setInputSize(input);
            }

            int detectionCount;
            try {
                detector.detect(image, detections);
                detectionCount = detections.rows();
            } catch (Throwable detectionFailure) {
                log.warn("Face sample {} detection exception: dimensions={}x{}, channels=3, error={}",
                        sample, image.cols(), image.rows(), detectionFailure.toString());
                throw new FaceProcessingException(FaceProcessingException.Reason.DETECTION,
                        "Face detection failed. Check camera framing and retry.", detectionFailure);
            }
            log.info("Face sample {} detection count={}, dimensions={}x{}, channels=3, depth=8-bit",
                    sample, detectionCount, image.cols(), image.rows());
            if (detectionCount == 0) log.warn("Face sample {} rejected: zero faces detected", sample);
            else if (detectionCount > 1) log.warn("Face sample {} rejected: multiple faces detected (count={})", sample, detectionCount);
            FaceDetectionPolicy.requireExactlyOne(detectionCount);

            float width;
            float height;

            try (FloatPointer detection = new FloatPointer(detections.ptr(0))) {
                width = detection.get(2);
                height = detection.get(3);
            }

            if (width < 100 || height < 100
                    || width > image.cols() || height > image.rows()) {
                throw new FaceProcessingException(FaceProcessingException.Reason.FACE_SIZE,"Move closer and keep your face centered.");
            }

            try (Mat face = detections.row(0)) {
                recognizer.alignCrop(image, face, aligned);
            }

            recognizer.feature(aligned, feature);

            if (feature.total() * feature.channels() != 128
                    || feature.depth() != opencv_core.CV_32F) {
                throw new FaceProcessingException(FaceProcessingException.Reason.EMBEDDING,"The face model returned an unsupported feature.");
            }

            float[] values = new float[128];

            try (FloatPointer pointer = new FloatPointer(feature.ptr(0))) {
                pointer.get(values);
            }

            try { return new FaceEmbedding(values); }
            catch (RuntimeException invalidEmbedding) {
                log.warn("Face sample {} embedding validation failed: dimensions={}x{}, error={}", sample,
                        feature.cols(), feature.rows(), invalidEmbedding.toString());
                throw new FaceProcessingException(FaceProcessingException.Reason.EMBEDDING,
                        "The face feature was invalid and could not be used.", invalidEmbedding);
            }
            finally { java.util.Arrays.fill(values, 0f); }
        } catch (FaceProcessingException e) {
            if (e.reason() == FaceProcessingException.Reason.ALIGNMENT || e.reason() == FaceProcessingException.Reason.EMBEDDING)
                log.warn("Face sample {} processing failed at {}: dimensions={}x{}, error={}",
                        sample, e.reason(), image.cols(), image.rows(), e.toString());
            throw e;
        } catch (Throwable e) {
            log.warn("Face sample {} processing exception: dimensions={}x{}, error={}", sample, image.cols(), image.rows(), e.toString());
            throw new FaceProcessingException(FaceProcessingException.Reason.ALIGNMENT,"The face could not be aligned or processed.", e);
        } finally {
            detections.close();
            aligned.close();
            feature.close();
        }
    }

    @Override public synchronized void close() {
        disposed=true;
        if (detector != null) { detector.close(); detector = null; }
        if (recognizer != null) { recognizer.close(); recognizer = null; }
        initialized.set(false);
    }
}
