package com.estateflow.dao;

import com.estateflow.exception.EstateFlowException;

/** Transaction boundary used by services; implemented in the db package (Section 3.4). */
public interface TransactionManager {

    <T> T inTransaction(TransactionalWork<T> work) throws EstateFlowException;

    /** Work without a result. Named differently from the generic method to keep lambdas unambiguous. */
    default void runInTransaction(VoidWork work) throws EstateFlowException {
        inTransaction(() -> {
            work.execute();
            return null;
        });
    }

    @FunctionalInterface
    interface VoidWork {
        void execute() throws EstateFlowException;
    }
}
