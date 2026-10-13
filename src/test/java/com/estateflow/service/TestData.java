package com.estateflow.service;

import com.estateflow.model.*;
import com.estateflow.model.enums.PropertyType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

final class TestData {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T08:00:00Z"), ZoneOffset.UTC);
    static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    static Owner owner(Long id, boolean active) {
        Owner o = new Owner("Asha Verma", "+91 98765-43210", "asha@example.com", "Amritsar");
        o.setId(id);
        o.setActive(active);
        return o;
    }

    static Tenant tenant(Long id, boolean active) {
        Tenant t = new Tenant("Ravi Kumar", "9876543211", "ravi@example.com", "Jalandhar");
        t.setId(id);
        t.setActive(active);
        return t;
    }

    static Property property(Long id, Long ownerId, boolean active) {
        Property p = new Property();
        p.setPropertyId(id);
        p.setOwnerId(ownerId);
        p.setName("Flat 4B");
        p.setPropertyType(PropertyType.APARTMENT);
        p.setAddressLine("12 Mall Road");
        p.setCity("Amritsar");
        p.setListedRent(new BigDecimal("15000.00"));
        p.setActive(active);
        return p;
    }

    static RentCharge charge(Long id, LocalDate due, String amountDue, String paid) {
        RentCharge c = new RentCharge(7L, due, due.plusMonths(1).minusDays(1), due, new BigDecimal(amountDue));
        c.setChargeId(id);
        c.setAmountPaid(new BigDecimal(paid));
        return c;
    }

    private TestData() {
    }
}
