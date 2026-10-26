package com.estateflow.model;
public record Vendor(Long id,String name,VendorCategory category,String phone,String email,String address,String notes,boolean active) { }
