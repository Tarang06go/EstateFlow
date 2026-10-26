package com.estateflow.dao;

import com.estateflow.model.FaceEnrollmentRecord;

import java.util.Optional;

public interface BiometricDao {
    Optional<FaceEnrollmentRecord> find(long userId, String deviceId);
    void save(FaceEnrollmentRecord enrollment);
    int setEnabled(long userId, String deviceId, boolean enabled);
    int deleteDevice(long userId, String deviceId);
    int deleteAllForUser(long userId);
}
