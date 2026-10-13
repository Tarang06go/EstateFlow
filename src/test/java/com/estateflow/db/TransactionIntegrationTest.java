package com.estateflow.db;

import com.estateflow.dao.jdbc.JdbcOwnerDao;
import com.estateflow.exception.*;
import com.estateflow.model.Owner;
import com.estateflow.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TransactionIntegrationTest extends IntegrationTestBase {

    private static boolean isClosed(Connection c) {
        try {
            return c.isClosed();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Opens a transaction on the connection, then kills it server-side so commit and rollback both fail. */
    private void killServerSide(Connection victim) {
        try (java.sql.Statement st = victim.createStatement()) {
            long id;
            st.execute("INSERT INTO owner (full_name, phone) VALUES ('Victim', '9000000777')"); // really open a transaction
            try (java.sql.ResultSet rs = st.executeQuery("SELECT CONNECTION_ID()")) {
                rs.next();
                id = rs.getLong(1);
            }
            try (Connection other = db().openConnection(); java.sql.Statement kill = other.createStatement()) {
                kill.execute("KILL " + id);
            }
            for (int i = 0; i < 40 && victim.isValid(1); i++) {
                Thread.sleep(50);
            }
        } catch (SQLException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    DatabaseManager db() {
        return ctx.databaseManager();
    }

    JdbcTransactionManager tx() {
        return new JdbcTransactionManager(db());
    }

    JdbcOwnerDao owners() {
        return new JdbcOwnerDao(db());
    }

    private Owner owner(String phone) {
        return new Owner("Test Owner", phone, null, null);
    }

    @Test
    @DisplayName("Section 3.4: owned handle really closes outside a transaction")
    void acquire_outsideTransaction_isOwnedAndClosed() throws Exception {
        ConnectionHandle h = db().acquire();
        Connection c = h.connection();
        assertTrue(h.isOwned());
        h.close();
        assertTrue(c.isClosed());
    }

    @Test
    @DisplayName("Section 3.4: borrowed handle is not closed inside a transaction; connection closed at the end")
    void acquire_insideTransaction_isBorrowedAndSurvivesDaoCalls() throws Exception {
        AtomicReference<Connection> seen = new AtomicReference<>();
        tx().runInTransaction(() -> {
            try (ConnectionHandle h = db().acquire()) {
                assertFalse(h.isOwned());
                seen.set(h.connection());
            }
            owners().insert(owner("9000000001")); // a DAO call closes its handle; the transaction must survive
            try (ConnectionHandle h = db().acquire()) {
                assertSame(seen.get(), h.connection());
                assertFalse(isClosed(h.connection()));
            }
        });
        assertTrue(seen.get().isClosed());
        assertTrue(owners().findByPhone("9000000001").isPresent());
    }

    @Test
    @DisplayName("Section 3.4: nested transactions join the outer one and share the connection")
    void inTransaction_nested_joinsOuter() throws Exception {
        AtomicReference<Connection> outer = new AtomicReference<>();
        AtomicReference<Connection> inner = new AtomicReference<>();
        tx().runInTransaction(() -> {
            try (ConnectionHandle h = db().acquire()) {
                outer.set(h.connection());
            }
            tx().runInTransaction(() -> {
                try (ConnectionHandle h = db().acquire()) {
                    inner.set(h.connection());
                }
                owners().insert(owner("9000000002"));
            });
            assertFalse(isClosed(outer.get()));
        });
        assertSame(outer.get(), inner.get());
        assertTrue(owners().findByPhone("9000000002").isPresent());
    }

    @Test
    @DisplayName("Section 3.4: a checked exception rolls back and is rethrown unchanged")
    void inTransaction_checkedException_rollsBackAndRethrows() {
        BusinessRuleException boom = new BusinessRuleException("boom", "BR-99");
        BusinessRuleException thrown = assertThrows(BusinessRuleException.class, () -> tx().runInTransaction(() -> {
            owners().insert(owner("9000000003"));
            throw boom;
        }));
        assertSame(boom, thrown);
        assertTrue(owners().findByPhone("9000000003").isEmpty());
    }

    @Test
    @DisplayName("Section 3.4: an unchecked exception rolls back and is rethrown unchanged")
    void inTransaction_uncheckedException_rollsBackAndRethrows() {
        IllegalStateException boom = new IllegalStateException("boom");
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> tx().runInTransaction(() -> {
            owners().insert(owner("9000000004"));
            throw boom;
        }));
        assertSame(boom, thrown);
        assertTrue(owners().findByPhone("9000000004").isEmpty());
    }

    @Test
    @DisplayName("Section 3.4: a failing rollback is attached with addSuppressed to the original exception")
    void inTransaction_rollbackFailure_isSuppressedOnOriginal() {
        IllegalStateException boom = new IllegalStateException("boom");
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> tx().runInTransaction(() -> {
            try (ConnectionHandle h = db().acquire()) {
                killServerSide(h.connection()); // sabotage: rollback on a dead connection fails
            }
            throw boom;
        }));
        assertSame(boom, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertInstanceOf(SQLException.class, thrown.getSuppressed()[0]);
    }

    @Test
    @DisplayName("Section 3.4: a failed commit surfaces as DataAccessException with the SQLException as cause")
    void inTransaction_commitFailure_throwsDataAccessExceptionWithCause() {
        DataAccessException e = assertThrows(DataAccessException.class, () -> tx().runInTransaction(() -> {
            try (ConnectionHandle h = db().acquire()) {
                killServerSide(h.connection()); // commit will fail
            }
        }));
        assertNotNull(e.getCause());
        assertInstanceOf(SQLException.class, e.getCause());
    }

    @Test
    @DisplayName("After a transaction ends nothing stays bound: the next acquire owns a fresh connection")
    void afterTransaction_threadBindingIsCleared() throws Exception {
        assertThrows(BusinessRuleException.class, () -> tx().runInTransaction(() -> {
            throw new BusinessRuleException("x", "BR-99");
        }));
        try (ConnectionHandle h = db().acquire()) {
            assertTrue(h.isOwned());
        }
    }
}
