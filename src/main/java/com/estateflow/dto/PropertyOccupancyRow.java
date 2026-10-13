package com.estateflow.dto;

import java.math.BigDecimal;

/** Property list / occupancy row: occupancy is derived from agreements (BR-17). */
public record PropertyOccupancyRow(
        long propertyId, String propertyName, String city, String ownerName, boolean active,
        boolean occupied, String tenantName, BigDecimal listedRent) {
}
