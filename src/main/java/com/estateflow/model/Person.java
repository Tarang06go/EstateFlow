package com.estateflow.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Abstract base for people tracked by EstateFlow (owners and tenants).
 * Provides shared contact state.
 */
public abstract class Person {

    private Long id;
    private String fullName;
    private String phone;
    private String email;
    private String address;
    private boolean active;
    private LocalDateTime createdAt;

    protected Person() {
        this.active = true;
    }

    protected Person(String fullName, String phone, String email, String address) {
        this.fullName = Objects.requireNonNull(fullName, "fullName is required");
        this.phone = Objects.requireNonNull(phone, "phone is required");
        this.email = email;
        this.address = address;
        this.active = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = Objects.requireNonNull(fullName, "fullName is required"); }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = Objects.requireNonNull(phone, "phone is required"); }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * Display name for UI purposes.
     */
    public String getDisplayName() {
        return fullName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person person = (Person) o;
        if (id == null || person.id == null) return false;
        return id.equals(person.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + id + ", fullName='" + fullName + "'}";
    }
}
