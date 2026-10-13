package com.estateflow.dao;

import com.estateflow.model.Tenant;

import java.util.List;
import java.util.Optional;

public interface TenantDao extends CrudDao<Tenant, Long> {
    Optional<Tenant> findByPhone(String phone);

    Optional<Tenant> findByIdProof(String type, String number);

    List<Tenant> search(String text);

    List<Tenant> search(String text, int limit);
}
