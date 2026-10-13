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
    private static final String DEFAULTS_RESOURCE = "config/application.properties";

    private final Properties values;

    private AppConfig(Properties values) {
        this.values = values;
    }

    /** Loads defaults and the external db.properties (system property, ./db.properties or ./db/db.properties). */
    public static AppConfig load() {
        String explicit = System.getProperty(DB_PROPERTIES_PROPERTY);
        if (explicit != null && !explicit.isBlank()) {
            return load(Path.of(explicit));
        }
        for (String candidate : new String[]{"db.properties", "db/db.properties"}) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                return load(path);
            }
        }
        throw new ConfigurationException("Database configuration not found. Copy db/db.properties.example to "
                + "db.properties, fill in your MySQL credentials, and start the application from the project folder.");
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
            throw new ConfigurationException("Database configuration file not found: " + dbProperties);
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
}
