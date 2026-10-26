package com.estateflow.dto;

import com.estateflow.model.Owner;

import java.math.BigDecimal;
import java.util.List;

public record OwnerDetails(Owner owner, List<PropertyOccupancyRow> properties, BigDecimal outstandingBalance) {
}
