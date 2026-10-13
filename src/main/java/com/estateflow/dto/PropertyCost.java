package com.estateflow.dto;

import java.math.BigDecimal;

public record PropertyCost(long propertyId, String propertyName, int requests, BigDecimal cost) {
}
