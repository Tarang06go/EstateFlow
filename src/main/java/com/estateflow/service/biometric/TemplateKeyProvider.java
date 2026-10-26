package com.estateflow.service.biometric;

import java.util.Optional;

public interface TemplateKeyProvider {
    String deviceId();
    byte[] createOrLoadKey(long userId);
    Optional<byte[]> loadExistingKey(long userId);
    void deleteKey(long userId);
}
