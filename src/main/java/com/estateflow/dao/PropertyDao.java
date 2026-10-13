package com.estateflow.dao;

import com.estateflow.model.Property;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PropertyDao extends CrudDao<Property, Long> {
    List<Property> findByOwner(Long ownerId);

    List<Property> search(String text);

    List<Property> findVacantOn(LocalDate date);

    /** Locking read; only valid inside a transaction and must be its first data access. */
    Optional<Property> findByIdForUpdate(Long propertyId);

    int countActiveByOwner(Long ownerId);

    List<Property> search(String text, int limit);
}
