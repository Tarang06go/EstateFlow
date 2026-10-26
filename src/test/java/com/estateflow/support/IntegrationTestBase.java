package com.estateflow.support;

import com.estateflow.config.AppConfig;
import com.estateflow.config.AppContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Assumptions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Real-MySQL tests: skipped (not failed) when no db-test.properties is configured. */
@Tag("integration")
public abstract class IntegrationTestBase {

    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T08:00:00Z"), ZoneOffset.UTC);
    public static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    protected static AppContext ctx;

    @BeforeAll
    static void connect() {
        Assumptions.assumeTrue(TestDatabase.isConfigured(),
                "db-test.properties not found: MySQL integration tests are skipped");
        AppConfig config = TestDatabase.config().orElseThrow();
        ctx = new AppContext(config, CLOCK);
    }

    @BeforeEach
    void cleanDatabase() throws Exception {
        TestDatabase.truncateAll(ctx.databaseManager());
        // Phase 1: services require a signed-in user. Existing workflow tests run as an administrator;
        // AuthIntegrationTest clears this session to exercise the real login path.
        ctx.session().login(new com.estateflow.model.AppUser(1L, "it-admin", com.estateflow.model.enums.Role.ADMIN, true,
                java.time.LocalDateTime.of(2026, 1, 1, 0, 0), null));
    }
}
