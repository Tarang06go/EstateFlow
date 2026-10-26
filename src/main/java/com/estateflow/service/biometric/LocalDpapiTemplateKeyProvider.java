package com.estateflow.service.biometric;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/** Stores only a DPAPI CurrentUser-wrapped AES key under the current Windows user's Local AppData. */
public final class LocalDpapiTemplateKeyProvider implements TemplateKeyProvider {
    private static final byte[] MAGIC = "EFK1".getBytes(StandardCharsets.US_ASCII);
    private final Path directory;
    private final Path deviceFile;
    private final KeyProtector protector;
    private final SecureRandom random = new SecureRandom();
    private volatile String deviceId;

    public LocalDpapiTemplateKeyProvider(KeyProtector protector) { this(protector, defaultDirectory()); }
    LocalDpapiTemplateKeyProvider(KeyProtector protector, Path directory) {
        this.protector = protector; this.directory = directory.toAbsolutePath().normalize();
        this.deviceFile = this.directory.resolve("device-id.txt");
    }
    public static Path defaultDirectory() {
        String local = System.getenv("LOCALAPPDATA");
        if (local == null || local.isBlank()) throw new IllegalStateException("Windows Local AppData is unavailable.");
        return Path.of(local, "EstateFlow", "biometric");
    }

    @Override public synchronized String deviceId() {
        if (deviceId != null) return deviceId;
        try {
            Files.createDirectories(directory);
            if (Files.isSymbolicLink(directory)) throw new IOException("Biometric key directory cannot be a symbolic link.");
            if (Files.exists(deviceFile)) {
                if (Files.isSymbolicLink(deviceFile)) throw new IOException("Biometric device identity cannot be a symbolic link.");
                String value = Files.readString(deviceFile, StandardCharsets.US_ASCII).trim();
                deviceId = UUID.fromString(value).toString();
            } else {
                String value = UUID.randomUUID().toString();
                writeNew(deviceFile, value.getBytes(StandardCharsets.US_ASCII));
                deviceId = value;
            }
            return deviceId;
        } catch (IOException | IllegalArgumentException e) { throw new IllegalStateException("The local biometric device identity is unavailable.", e); }
    }

    @Override public synchronized Optional<byte[]> loadExistingKey(long userId) {
        Path keyFile=keyFile(userId);
        if (!Files.exists(keyFile)) return Optional.empty();
        try {
            if (Files.isSymbolicLink(keyFile)) throw new IOException("Biometric key file cannot be a symbolic link.");
            byte[] encoded = Files.readAllBytes(keyFile);
            if (encoded.length < 5 || encoded.length > 4096 || !Arrays.equals(MAGIC, Arrays.copyOf(encoded, 4)))
                throw new IOException("Biometric key file is malformed.");
            byte[] wrapped = Arrays.copyOfRange(encoded, 4, encoded.length);
            byte[] key=null;
            try {
                key=protector.unprotect(wrapped);
                if (key.length != 32) { Arrays.fill(key, (byte) 0); throw new IOException("Unwrapped biometric key has an invalid size."); }
                return Optional.of(key);
            } finally { Arrays.fill(wrapped, (byte) 0); Arrays.fill(encoded, (byte) 0); }
        } catch (IOException | RuntimeException e) { throw new IllegalStateException("The local biometric key cannot be opened; password sign-in remains available and face enrollment must be repeated.", e); }
    }

    @Override public synchronized byte[] createOrLoadKey(long userId) {
        Path keyFile=keyFile(userId);
        Optional<byte[]> existing = loadExistingKey(userId);
        if (existing.isPresent()) return existing.get();
        byte[] key = new byte[32]; random.nextBytes(key);
        byte[] wrapped = null, fileBytes = null;
        try {
            deviceId(); wrapped = protector.protect(key);
            fileBytes = ByteBuffer.allocate(4 + wrapped.length).put(MAGIC).put(wrapped).array();
            writeNew(keyFile, fileBytes);
            return key;
        } catch (FileAlreadyExistsException e) {
            Arrays.fill(key, (byte) 0);
            return loadExistingKey(userId).orElseThrow(() -> new IllegalStateException("Could not load the existing biometric key.", e));
        } catch (IOException | RuntimeException e) {
            Arrays.fill(key, (byte) 0);
            throw new IllegalStateException("A protected biometric key could not be created; no plaintext fallback was used.", e);
        } finally {
            if (wrapped != null) Arrays.fill(wrapped, (byte) 0);
            if (fileBytes != null) Arrays.fill(fileBytes, (byte) 0);
        }
    }

    @Override public synchronized void deleteKey(long userId) {
        try { Files.deleteIfExists(keyFile(userId)); }
        catch(IOException e) { throw new IllegalStateException("The local protected biometric key could not be deleted.",e); }
    }

    private Path keyFile(long userId) {
        if(userId<1) throw new IllegalArgumentException("A valid account is required for a face key.");
        return directory.resolve("face-key-"+userId+".dpapi");
    }

    private void writeNew(Path target, byte[] data) throws IOException {
        Path temporary = Files.createTempFile(directory, ".face-key-", ".tmp");
        try {
            Files.write(temporary, data);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
