package com.estateflow.dao;

import com.estateflow.model.Owner;

import java.util.List;
import java.util.Optional;

public interface OwnerDao extends CrudDao<Owner, Long> {
    Optional<Owner> findByPhone(String phone);

    List<Owner> search(String text);

    /** Capped search for the global search box. */
    List<Owner> search(String text, int limit);
}
