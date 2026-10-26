package com.estateflow.service.biometric;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Fixed, integrity-checked model files; EstateFlow never downloads models at runtime. */
public class FaceModelFiles {
    public static final String YUNET_NAME = "face_detection_yunet_2023mar.onnx";
    public static final String SFACE_NAME = "face_recognition_sface_2021dec.onnx";
    public static final String SFACE_VERSION = "sface-2021dec-v1";
    private static final String YUNET_SHA256 = "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4";
    private static final String SFACE_SHA256 = "0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79";
    private final Path directory;

    public FaceModelFiles(Path directory) { this.directory = directory; }
    public static Path defaultDirectory() {
        String configured = System.getProperty("estateflow.face.models.dir");
        if (configured == null || configured.isBlank()) configured = System.getenv("ESTATEFLOW_FACE_MODELS");
        if (configured == null || configured.isBlank()) {
            String local = System.getenv("LOCALAPPDATA");
            if (local == null || local.isBlank()) return Path.of(System.getProperty("user.home"), "AppData", "Local", "EstateFlow", "models");
            configured = Path.of(local, "EstateFlow", "models").toString();
        }
        return Path.of(configured).toAbsolutePath().normalize();
    }
    public Path yunet() throws IOException { return verified(YUNET_NAME, YUNET_SHA256); }
    public Path sface() throws IOException { return verified(SFACE_NAME, SFACE_SHA256); }
    public boolean available() {
        try { yunet(); sface(); return true; } catch (IOException | RuntimeException e) { return false; }
    }
    private Path verified(String name, String expected) throws IOException {
        Path path = directory.resolve(name).normalize();
        if (!path.startsWith(directory.normalize()) || !Files.isRegularFile(path)) throw new IOException("Required face model is unavailable.");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            if (!MessageDigest.isEqual(HexFormat.of().parseHex(expected), digest)) throw new IOException("Face model integrity check failed.");
            return path;
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 is unavailable.", e); }
    }
}
