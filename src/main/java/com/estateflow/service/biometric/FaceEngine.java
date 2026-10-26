package com.estateflow.service.biometric;

public interface FaceEngine extends AutoCloseable {
    boolean available();
    FaceEmbedding extract(CameraFrame frame) throws FaceProcessingException;
    @Override default void close() { }
}
