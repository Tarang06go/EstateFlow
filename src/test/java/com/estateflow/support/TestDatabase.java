package com.estateflow.support;

import com.estateflow.config.AppConfig;
import com.estateflow.db.ConnectionHandle;
import com.estateflow.db.DatabaseManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * Safety harness for integration tests. It only ever reads the explicit, uncommitted db-test.properties
 * (or the file named by -Destateflow.test.config), never falls back to development credentials, and
 * refuses to truncate anything unless the connected schema name ends with _test.
 */
public final class TestDatabase {

    public static final String CONFIG_PROPERTY = "estateflow.test.config";
    private static final List<String> TABLES_CHILD_FIRST = List.of("payment", "rent_charge", "rental_agreement",
            "maintenance_request", "property", "tenant", "owner");

    private TestDatabase() {
    }

    public static Path configPath() {
        return Path.of(System.getProperty(CONFIG_PROPERTY, "db-test.properties"));
    }

    public static boolean isConfigured() {
        return Files.isRegularFile(configPath());
    }

    public static Optional<AppConfig> config() {
        if (!isConfigured()) {
            return Optional.empty();
        }
        AppConfig config = AppConfig.load(configPath());
        requireTestSchemaInUrl(config.dbUrl());
        return Optional.of(config);
    }

    /** Throws unless the schema named in the JDBC URL ends with _test. */
    public static void requireTestSchemaInUrl(String url) {
        String schema = schemaName(url);
        if (schema == null || !schema.endsWith("_test")) {
            throw new IllegalStateException("Refusing to run: integration tests may only target a schema whose name "
                    + "ends with _test, but the configured schema is '" + schema + "'.");
        }
    }

    static String schemaName(String url) {
        if (url == null) {
            return null;
        }
        int slash = url.indexOf('/', url.indexOf("//") + 2);
        if (slash < 0) {
            return null;
        }
        String rest = url.substring(slash + 1);
        int q = rest.indexOf('?');
        return q >= 0 ? rest.substring(0, q) : rest;
    }

    /** Empties all seven tables; re-checks the live schema name first and restores FK checks in finally. */
    public static void truncateAll(DatabaseManager db) throws SQLException {
        try (ConnectionHandle handle = db.acquire(); Statement st = handle.connection().createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
                rs.next();
                String live = rs.getString(1);
                if (live == null || !live.endsWith("_test")) {
                    throw new IllegalStateException("Refusing to truncate schema '" + live + "': name must end with _test");
                }
            }
            boolean hasAudit;
            try (ResultSet rs = st.executeQuery("SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() "
                    + "AND table_name = 'audit_log'")) {
                hasAudit = rs.next();
            }
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                for (String table : TABLES_CHILD_FIRST) {
                    st.execute("TRUNCATE TABLE " + table);
                }
                if (hasAudit) {
                    st.execute("TRUNCATE TABLE audit_log"); // v1.1 table, present only after the migration
                }
            } finally {
                st.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        }
    }
}
