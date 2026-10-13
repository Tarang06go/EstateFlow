package com.estateflow.db;

import com.estateflow.dao.TransactionManager;
import com.estateflow.dao.TransactionalWork;
import com.estateflow.exception.DataAccessException;
import com.estateflow.exception.EstateFlowException;

import java.sql.Connection;
import java.sql.SQLException;

/** Implements the transaction rules of Section 3.4. Never runs SQL besides commit/rollback. */
public class JdbcTransactionManager implements TransactionManager {

    private final DatabaseManager databaseManager;

    public JdbcTransactionManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public <T> T inTransaction(TransactionalWork<T> work) throws EstateFlowException {
        if (databaseManager.isTransactionBound()) {
            return work.execute(); // join the outer transaction; only the outermost call commits
        }
        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            databaseManager.bind(connection);
            try {
                T result = work.execute();
                connection.commit();
                return result;
            } catch (Throwable failure) {
                rollback(connection, failure);
                throw failure;
            } finally {
                databaseManager.unbind();
            }
        } catch (SQLException e) {
            throw new DataAccessException("transaction failed [SQLState=" + e.getSQLState()
                    + ", code=" + e.getErrorCode() + "]", e);
        }
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }
}
