package com.estateflow.dao;

import com.estateflow.exception.EstateFlowException;

@FunctionalInterface
public interface TransactionalWork<T> {
    T execute() throws EstateFlowException;
}
