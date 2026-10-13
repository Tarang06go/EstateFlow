package com.estateflow.support;

import com.estateflow.config.AppContext;
import com.estateflow.model.*;
import com.estateflow.model.enums.PropertyType;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

/** Creates valid parties through the real services. */
public final class Fixtures {
    private static final AtomicInteger SEQ = new AtomicInteger(1000);

    private Fixtures() {
    }

    public static Owner owner(AppContext ctx) throws Exception {
        int n = SEQ.incrementAndGet();
        return ctx.owners().create(new Owner("Owner " + n, "98000" + n + "0", null, null));
    }

    public static Tenant tenant(AppContext ctx) throws Exception {
        int n = SEQ.incrementAndGet();
        return ctx.tenants().create(new Tenant("Tenant " + n, "97000" + n + "0", null, null));
    }

    public static Property property(AppContext ctx, Owner owner) throws Exception {
        Property p = new Property();
        p.setOwnerId(owner.getId());
        p.setName("Flat " + SEQ.incrementAndGet());
        p.setPropertyType(PropertyType.APARTMENT);
        p.setAddressLine("1 Test Road");
        p.setCity("Patiala");
        p.setListedRent(new BigDecimal("15000.00"));
        return ctx.properties().create(p);
    }
}
