package com.estateflow.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigTest {
    @TempDir Path temp;

    @Test void explicitSystemPathHasHighestPriority() {
        Path result = AppConfig.resolveDbPropertiesPath("custom/db.properties", "env.properties",
                temp.resolve("home"), temp);
        assertEquals(Path.of("custom/db.properties"), result);
    }

    @Test void environmentPathIsUsedBeforeDefaults() {
        Path result = AppConfig.resolveDbPropertiesPath(" ", "env/db.properties", temp.resolve("home"), temp);
        assertEquals(Path.of("env/db.properties"), result);
    }

    @Test void userConfigWinsAndLegacyProjectConfigRemainsSupported() throws Exception {
        Path home = temp.resolve("home");
        Path userConfig = home.resolve(".estateflow/db.properties");
        Files.createDirectories(userConfig.getParent());
        Files.writeString(userConfig, "local configuration placeholder");
        Path legacy = temp.resolve("db.properties");
        Files.writeString(legacy, "legacy configuration placeholder");
        assertEquals(userConfig, AppConfig.resolveDbPropertiesPath(null, null, home, temp));

        Files.delete(userConfig);
        assertEquals(legacy, AppConfig.resolveDbPropertiesPath(null, null, home, temp));
    }

    @Test void faceSettingsHaveSafeDefaultsAndAllowExplicitModelDirectory() throws Exception {
        Path db=temp.resolve("db.properties");
        Files.writeString(db,"db.url=jdbc:mysql://localhost:3307/estateflow_test\ndb.username=test\ndb.password=test\nface.models.directory=C:/EstateFlow/models\nface.match.threshold=0.42\nface.camera.timeout.seconds=12\n");
        AppConfig config=AppConfig.load(db);
        assertEquals(Path.of("C:/EstateFlow/models").toAbsolutePath().normalize(),config.faceModelsDirectory());
        assertEquals(.42,config.faceMatchThreshold(),.00001);
        assertEquals(.70,config.faceDetectionScoreThreshold(),.00001);
        assertEquals(12,config.faceCameraTimeoutSeconds());
        assertEquals(false,config.faceLoginEnabled());
    }

    @Test void faceDetectionThresholdCanBeConfiguredWithinSafeRange() throws Exception {
        Path db=temp.resolve("face-threshold.properties");
        Files.writeString(db,"db.url=jdbc:mysql://localhost/estateflow_test\ndb.username=test\ndb.password=test\nface.detection.score.threshold=0.75\n");
        assertEquals(.75,AppConfig.load(db).faceDetectionScoreThreshold(),.00001);
        Files.writeString(db,"db.url=jdbc:mysql://localhost/estateflow_test\ndb.username=test\ndb.password=test\nface.detection.score.threshold=0.2\n");
        assertThrows(com.estateflow.exception.ConfigurationException.class,()->AppConfig.load(db));
    }

    @Test void faceLoginSystemPropertyOverridesConfigWhileDefaultRemainsOff() throws Exception {
        Path db=temp.resolve("face-login-override.properties");
        Files.writeString(db,"db.url=jdbc:mysql://localhost/estateflow_test\ndb.username=test\ndb.password=test\nface.login.enabled=true\n");
        String previous=System.getProperty("estateflow.face.login.enabled");
        try {
            System.clearProperty("estateflow.face.login.enabled");
            assertEquals(true,AppConfig.load(db).faceLoginEnabled(),"The property file setting remains usable when no override exists.");
            System.setProperty("estateflow.face.login.enabled","false");
            assertEquals(false,AppConfig.load(db).faceLoginEnabled(),"The temporary system override takes precedence.");
            System.setProperty("estateflow.face.login.enabled","true");
            assertEquals(true,AppConfig.load(db).faceLoginEnabled());
            System.setProperty("estateflow.face.login.enabled","invalid");
            assertThrows(com.estateflow.exception.ConfigurationException.class,()->AppConfig.load(db));
        } finally {
            if(previous==null) System.clearProperty("estateflow.face.login.enabled");
            else System.setProperty("estateflow.face.login.enabled",previous);
        }
    }

    @Test void invalidFaceThresholdIsRejected() throws Exception {
        Path db=temp.resolve("bad-db.properties");
        Files.writeString(db,"db.url=jdbc:mysql://localhost/estateflow_test\ndb.username=test\ndb.password=test\nface.match.threshold=2\n");
        assertThrows(com.estateflow.exception.ConfigurationException.class,()->AppConfig.load(db));
    }

    @Test void generalAssistantProviderIsDisabledAndRejectsUninstalledProviders() throws Exception {
        Path db=temp.resolve("assistant-provider.properties");
        Files.writeString(db,"db.url=jdbc:mysql://localhost/estateflow_test\ndb.username=test\ndb.password=test\n");
        assertEquals("none",AppConfig.load(db).assistantProvider());
        Files.writeString(db,"db.url=jdbc:mysql://localhost/estateflow_test\ndb.username=test\ndb.password=test\nassistant.provider=external\n");
        assertThrows(com.estateflow.exception.ConfigurationException.class,()->AppConfig.load(db));
    }
}
