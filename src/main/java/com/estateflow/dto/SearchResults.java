package com.estateflow.dto;

import com.estateflow.model.Owner;
import com.estateflow.model.Tenant;

import java.util.List;

/** Capped results of the global search, one list per entity type. */
public record SearchResults(List<Owner> owners, List<Tenant> tenants, List<PropertyOccupancyRow> properties,
                            List<AgreementListRow> agreements, List<PaymentReportRow> payments) {

    public static SearchResults empty() {
        return new SearchResults(List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public int total() {
        return owners.size() + tenants.size() + properties.size() + agreements.size() + payments.size();
    }
}
