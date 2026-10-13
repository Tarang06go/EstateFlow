package com.estateflow.model;

import com.estateflow.model.enums.PropertyType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A property belonging to one owner. Does not store occupancy status (derived).
 */
public class Property {

    private Long propertyId;
    private Long ownerId;
    private String name;
    private PropertyType propertyType;
    private String addressLine;
    private String city;
    private String state;
    private String postalCode;
    private Integer bedrooms;
    private BigDecimal areaSqft;
    private BigDecimal listedRent;
    private boolean active;
    private LocalDateTime createdAt;

    public Property() {
        this.active = true;
    }

    public Property(Long ownerId, String name, PropertyType propertyType,
                    String addressLine, String city, BigDecimal listedRent) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId is required");
        this.name = Objects.requireNonNull(name, "name is required");
        this.propertyType = Objects.requireNonNull(propertyType, "propertyType is required");
        this.addressLine = Objects.requireNonNull(addressLine, "addressLine is required");
        this.city = Objects.requireNonNull(city, "city is required");
        this.listedRent = Objects.requireNonNull(listedRent, "listedRent is required");
        this.active = true;
    }

    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public PropertyType getPropertyType() { return propertyType; }
    public void setPropertyType(PropertyType propertyType) { this.propertyType = propertyType; }

    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String addressLine) { this.addressLine = addressLine; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public Integer getBedrooms() { return bedrooms; }
    public void setBedrooms(Integer bedrooms) { this.bedrooms = bedrooms; }

    public BigDecimal getAreaSqft() { return areaSqft; }
    public void setAreaSqft(BigDecimal areaSqft) { this.areaSqft = areaSqft; }

    public BigDecimal getListedRent() { return listedRent; }
    public void setListedRent(BigDecimal listedRent) { this.listedRent = listedRent; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Property property = (Property) o;
        if (propertyId == null || property.propertyId == null) return false;
        return propertyId.equals(property.propertyId);
    }

    @Override
    public int hashCode() {
        return propertyId != null ? propertyId.hashCode() : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "Property{propertyId=" + propertyId + ", name='" + name + "', city='" + city + "'}";
    }
}
