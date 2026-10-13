package com.estateflow.service;

import com.estateflow.dao.TransactionManager;
import com.estateflow.dao.TransactionalWork;
import com.estateflow.exception.EstateFlowException;

/** Test double: runs the work directly with no database. */
public class PassThroughTransactionManager implements TransactionManager {
    public int calls;

    @Override
    public <T> T inTransaction(TransactionalWork<T> work) throws EstateFlowException {
        calls++;
        return work.execute();
    }
}
