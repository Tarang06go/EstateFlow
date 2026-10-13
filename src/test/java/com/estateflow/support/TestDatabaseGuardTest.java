package com.estateflow.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Runs without a database: proves the harness refuses non-_test schemas. */
class TestDatabaseGuardTest {

    @Test
    void requireTestSchema_developmentDatabase_isRefused() {
        assertThrows(IllegalStateException.class,
                () -> TestDatabase.requireTestSchemaInUrl("jdbc:mysql://localhost:3306/estateflow"));
        assertThrows(IllegalStateException.class,
                () -> TestDatabase.requireTestSchemaInUrl("jdbc:mysql://localhost:3306/estateflow?useSSL=false"));
        assertThrows(IllegalStateException.class, () -> TestDatabase.requireTestSchemaInUrl("jdbc:mysql://localhost:3306"));
    }

    @Test
    void requireTestSchema_testDatabase_isAccepted() {
        assertDoesNotThrow(() -> TestDatabase.requireTestSchemaInUrl("jdbc:mysql://localhost:3306/estateflow_test"));
        assertDoesNotThrow(() -> TestDatabase.requireTestSchemaInUrl("jdbc:mysql://h:3306/estateflow_test?x=1"));
        assertEquals("estateflow_test", TestDatabase.schemaName("jdbc:mysql://h:3306/estateflow_test?x=1"));
    }
}
