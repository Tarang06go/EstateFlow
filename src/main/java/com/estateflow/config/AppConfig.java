package com.estateflow.config;

import com.estateflow.exception.ConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Application settings: non-secret defaults from the classpath (config/application.properties) plus
 * database credentials from an external, uncommitted properties file.
 */
public final class AppConfig {

    public static final String DB_PROPERTIES_PROPERTY = "estateflow.db.config";
    public static final String DB_PROPERTIES_ENV = "ESTATEFLOW_DB_CONFIG";
    private static final String DEFAULTS_RESOURCE = "config/application.properties";

    private final Properties values;

    private AppConfig(Properties values) {
        this.values = values;
    }

    /** Loads defaults and the external database properties, preferring explicit, environment, then per-user config. */
    public static AppConfig load() {
        Path selected = resolveDbPropertiesPath(System.getProperty(DB_PROPERTIES_PROPERTY),
                System.getenv(DB_PROPERTIES_ENV), Path.of(System.getProperty("user.home")), Path.of("."));
        return load(selected);
    }

    static Path resolveDbPropertiesPath(String explicit, String environment, Path userHome, Path workingDirectory) {
        if (explicit != null && !explicit.isBlank()) return Path.of(explicit.trim());
        if (environment != null && !environment.isBlank()) return Path.of(environment.trim());
        Path perUser = userHome.resolve(".estateflow").resolve("db.properties");
        if (Files.isRegularFile(perUser)) return perUser;
        for (String candidate : new String[]{"db.properties", "db/db.properties"}) {
            Path legacy = workingDirectory.resolve(candidate);
            if (Files.isRegularFile(legacy)) return legacy;
        }
        return perUser;
    }

    /** Loads defaults and the given external database properties file. */
    public static AppConfig load(Path dbProperties) {
        Properties merged = new Properties();
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(DEFAULTS_RESOURCE)) {
            if (in != null) {
                merged.load(in);
            }
        } catch (IOException e) {
            throw new ConfigurationException("Could not read " + DEFAULTS_RESOURCE, e);
        }
        if (!Files.isRegularFile(dbProperties)) {
            throw new ConfigurationException("Database configuration file not found: " + dbProperties
                    + ". Copy db/db.properties.example to this path and add your MySQL connection details. "
                    + "The file is local and must not be shared or committed.");
        }
        try (InputStream in = Files.newInputStream(dbProperties)) {
            merged.load(in);
        } catch (IOException e) {
            throw new ConfigurationException("Could not read database configuration " + dbProperties, e);
        }
        AppConfig config = new AppConfig(merged);
        config.validate();
        return config;
    }

    private void validate() {
        String url = require("db.url");
        if (!url.startsWith("jdbc:")) {
            throw new ConfigurationException("db.url must be a JDBC URL starting with jdbc:");
        }
        String user = require("db.username");
        String password = values.getProperty("db.password");
        if (password == null || user.startsWith("YOUR_") || password.startsWith("REPLACE_WITH")) {
            throw new ConfigurationException("db.properties still contains the example placeholders. "
                    + "Enter your real MySQL username and password.");
        }
        int days = leaseExpiryWarningDays();
        if (days < 0 || days > 3650) {
            throw new ConfigurationException("lease.expiry.warning.days must be between 0 and 3650.");
        }
        int reminderDays = rentReminderDays();
        if (reminderDays < 0 || reminderDays > 90) {
            throw new ConfigurationException("rent.reminder.days must be between 0 and 90.");
        }
        faceMatchThreshold();
        faceDetectionScoreThreshold();
        faceCameraTimeoutSeconds();
        faceLoginEnabled();
        assistantProvider();
    }

    private String require(String key) {
        String v = values.getProperty(key);
        if (v == null || v.isBlank()) {
            throw new ConfigurationException("Missing configuration value: " + key);
        }
        return v.trim();
    }

    public String dbUrl() {
        return require("db.url");
    }

    public String dbUsername() {
        return require("db.username");
    }

    public String dbPassword() {
        return values.getProperty("db.password", "");
    }

    /** v1.1: how many days ahead the Rent Reminders screen lists upcoming rent, default 7. */
    public int rentReminderDays() {
        String raw = values.getProperty("rent.reminder.days", "7").trim();
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new ConfigurationException("rent.reminder.days must be a whole number but was '" + raw + "'", e);
        }
    }

    /** BR-27: lease-expiry warning window, default 30 days. */
    public int leaseExpiryWarningDays() {
        String raw = values.getProperty("lease.expiry.warning.days", "30").trim();
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new ConfigurationException("lease.expiry.warning.days must be a whole number but was '" + raw + "'", e);
        }
    }

    public Path faceModelsDirectory() {
        String configured=values.getProperty("face.models.directory", "").trim();
        if(configured.isEmpty()) {
            String local=System.getenv("LOCALAPPDATA");
            return (local==null || local.isBlank()?Path.of(System.getProperty("user.home"),"AppData","Local"):Path.of(local)).resolve("EstateFlow").resolve("models").toAbsolutePath().normalize();
        }
        return Path.of(configured).toAbsolutePath().normalize();
    }

    /** Initial experimental SFace cosine reference only; real-device calibration remains required. */
    public double faceMatchThreshold() {
        String raw=values.getProperty("face.match.threshold","0.363").trim();
        try { double value=Double.parseDouble(raw); if(Double.isFinite(value)&&value>=.363&&value<=.90)return value; }
        catch(NumberFormatException e) { throw new ConfigurationException("face.match.threshold must be between 0.363 and 0.90; values below the experimental reference are not allowed.",e); }
        throw new ConfigurationException("face.match.threshold must be between 0.363 and 0.90; values below the experimental reference are not allowed.");
    }

    /** YuNet detector confidence cutoff; 0.70 improves ordinary webcam recall while retaining detector confidence filtering. */
    public double faceDetectionScoreThreshold() {
        String raw=values.getProperty("face.detection.score.threshold","0.70").trim();
        try { double value=Double.parseDouble(raw); if(Double.isFinite(value)&&value>=.50&&value<=.90)return value; }
        catch(NumberFormatException e) { throw new ConfigurationException("face.detection.score.threshold must be between 0.50 and 0.90.",e); }
        throw new ConfigurationException("face.detection.score.threshold must be between 0.50 and 0.90.");
    }

    public int faceCameraTimeoutSeconds() {
        String raw=values.getProperty("face.camera.timeout.seconds","8").trim();
        try { int value=Integer.parseInt(raw); if(value>=2&&value<=30)return value; }
        catch(NumberFormatException e) { throw new ConfigurationException("face.camera.timeout.seconds must be a whole number from 2 to 30.",e); }
        throw new ConfigurationException("face.camera.timeout.seconds must be a whole number from 2 to 30.");
    }

    /** Global safety gate; face login stays disabled until model and hardware validation is completed. */
    public boolean faceLoginEnabled() {
        String override = System.getProperty("estateflow.face.login.enabled");
        String raw = (override == null ? values.getProperty("face.login.enabled", "false") : override).trim();

        if ("true".equalsIgnoreCase(raw)) return true;
        if ("false".equalsIgnoreCase(raw)) return false;

        throw new ConfigurationException("face.login.enabled must be true or false.");
    }

    /** General AI is explicitly off until a separately reviewed provider is implemented and configured. */
    public String assistantProvider() {
        String provider=values.getProperty("assistant.provider","none").trim().toLowerCase(java.util.Locale.ROOT);
        if (!"none".equals(provider)) {
            throw new ConfigurationException("assistant.provider currently supports only 'none'; no external AI provider is installed.");
        }
        return provider;
    }
}
