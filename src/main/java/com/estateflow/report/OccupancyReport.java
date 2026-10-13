package com.estateflow.report;

import com.estateflow.dto.PropertyOccupancyRow;

import java.util.List;

public class OccupancyReport implements ReportDefinition<PropertyOccupancyRow> {
    public String fileName() {
        return "occupancy.csv";
    }

    public List<String> headers() {
        return List.of("Property", "City", "Owner", "Active", "Occupied", "Tenant", "Listed Rent");
    }

    public List<String> toRow(PropertyOccupancyRow r) {
        return List.of(r.propertyName(), r.city(), r.ownerName(), r.active() ? "Yes" : "No",
                r.occupied() ? "Occupied" : "Vacant", r.tenantName() == null ? "" : r.tenantName(),
                r.listedRent().toPlainString());
    }
}
