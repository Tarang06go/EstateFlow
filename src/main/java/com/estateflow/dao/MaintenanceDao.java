package com.estateflow.dao;

import com.estateflow.model.MaintenanceRequest;
import com.estateflow.model.enums.MaintenancePriority;
import com.estateflow.model.enums.MaintenanceStatus;

import java.util.List;
import java.util.Map;

public interface MaintenanceDao extends CrudDao<MaintenanceRequest, Long> {
    List<MaintenanceRequest> findByProperty(Long propertyId);

    List<MaintenanceRequest> findByStatus(MaintenanceStatus status);

    /** Counts requests that are OPEN or IN_PROGRESS, grouped by priority. */
    Map<MaintenancePriority, Integer> countOpenByPriority();
}
