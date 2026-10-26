package com.estateflow.model.enums;

/** Kinds of deposit ledger entries. Transfers carry a deposit from a renewed agreement to its successor; they are not cash. */
public enum DepositType {
    COLLECTION("Collected"), DEDUCTION("Deduction"), REFUND("Refund"), TRANSFER_IN("Carried in"), TRANSFER_OUT("Carried out");

    private final String label;

    DepositType(String label) {
        this.label = label;
    }

    public String getDisplayLabel() {
        return label;
    }

    /** True for entries that add to the amount held. */
    public boolean credits() {
        return this == COLLECTION || this == TRANSFER_IN;
    }
}
