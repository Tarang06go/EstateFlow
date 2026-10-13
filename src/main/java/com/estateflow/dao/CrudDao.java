package com.estateflow.dao;

import java.util.List;
import java.util.Optional;

/**
 * Contract for entities that support insert, update, and lookup.
 * There is no delete anywhere: deactivation, cancellation, and voiding are updates.
 */
public interface CrudDao<T, ID> {

    T insert(T entity);

    /** @return number of matched rows; zero means the record is missing. */
    int update(T entity);

    Optional<T> findById(ID id);

    List<T> findAll();
}
