package com.estateflow.model;

/**
 * A property owner. Distinct type so an owner cannot be passed where a tenant is required.
 */
public class Owner extends Person {

    public Owner() {
        super();
    }

    public Owner(String fullName, String phone, String email, String address) {
        super(fullName, phone, email, address);
    }
}
