package com.estateflow.dao.jdbc;

import com.estateflow.dao.BiometricDao;
import com.estateflow.db.DatabaseManager;
import com.estateflow.model.FaceEnrollmentRecord;

import java.time.LocalDateTime;
import java.util.Optional;

public final class JdbcBiometricDao extends BaseJdbcDao implements BiometricDao {
    public JdbcBiometricDao(DatabaseManager manager) { super(manager); }
    @Override public Optional<FaceEnrollmentRecord> find(long userId, String deviceId) {
        return queryOne("find encrypted face enrollment", "SELECT user_id, device_id, template_ciphertext, nonce, key_version, template_version, model_version, consent_version, consented_at, enabled FROM user_face_enrollment WHERE user_id=? AND device_id=?",
                ps -> { ps.setLong(1,userId); ps.setString(2,deviceId); }, rs -> new FaceEnrollmentRecord(rs.getLong("user_id"),rs.getString("device_id"),rs.getBytes("template_ciphertext"),rs.getBytes("nonce"),rs.getInt("key_version"),rs.getInt("template_version"),rs.getString("model_version"),rs.getString("consent_version"),dateTime(rs,"consented_at"),rs.getBoolean("enabled")));
    }
    @Override public void save(FaceEnrollmentRecord e) {
        executeUpdate("save encrypted face enrollment", "INSERT INTO user_face_enrollment (user_id,device_id,template_ciphertext,nonce,key_version,template_version,model_version,consent_version,consented_at,enabled) VALUES (?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE template_ciphertext=VALUES(template_ciphertext),nonce=VALUES(nonce),key_version=VALUES(key_version),template_version=VALUES(template_version),model_version=VALUES(model_version),consent_version=VALUES(consent_version),consented_at=VALUES(consented_at),enabled=VALUES(enabled)", ps -> {
            ps.setLong(1,e.userId()); ps.setString(2,e.deviceId()); ps.setBytes(3,e.ciphertext()); ps.setBytes(4,e.nonce());
            ps.setInt(5,e.keyVersion()); ps.setInt(6,e.templateVersion()); ps.setString(7,e.modelVersion());
            ps.setString(8,e.consentVersion()); ps.setObject(9,e.consentedAt()); ps.setBoolean(10,e.enabled());
        });
    }
    @Override public int setEnabled(long userId,String deviceId,boolean enabled) {
        return executeUpdate("change face login state","UPDATE user_face_enrollment SET enabled=? WHERE user_id=? AND device_id=?",ps->{ps.setBoolean(1,enabled);ps.setLong(2,userId);ps.setString(3,deviceId);});
    }
    @Override public int deleteDevice(long userId,String deviceId) {
        return executeUpdate("delete face enrollment","DELETE FROM user_face_enrollment WHERE user_id=? AND device_id=?",ps->{ps.setLong(1,userId);ps.setString(2,deviceId);});
    }
    @Override public int deleteAllForUser(long userId) {
        return executeUpdate("revoke face enrollment","DELETE FROM user_face_enrollment WHERE user_id=?",ps->ps.setLong(1,userId));
    }
}
