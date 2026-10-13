package com.estateflow.model;

/**
 * A tenant with optional ID proof and emergency contact information.
 */
public class Tenant extends Person {

    private String idProofType;
    private String idProofNumber;
    private String emergencyContactName;
    private String emergencyContactPhone;

    public Tenant() {
        super();
    }

    public Tenant(String fullName, String phone, String email, String address) {
        super(fullName, phone, email, address);
    }

    public String getIdProofType() { return idProofType; }
    public void setIdProofType(String idProofType) { this.idProofType = idProofType; }

    public String getIdProofNumber() { return idProofNumber; }
    public void setIdProofNumber(String idProofNumber) { this.idProofNumber = idProofNumber; }

    public String getEmergencyContactName() { return emergencyContactName; }
    public void setEmergencyContactName(String emergencyContactName) { this.emergencyContactName = emergencyContactName; }

    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String emergencyContactPhone) { this.emergencyContactPhone = emergencyContactPhone; }

    @Override
    public String toString() {
        return "Tenant{id=" + getId() + ", fullName='" + getFullName() + "'}";
    }
}
