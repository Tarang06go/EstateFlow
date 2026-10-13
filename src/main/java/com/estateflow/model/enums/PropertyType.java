package com.estateflow.model.enums;

public enum PropertyType {
    APARTMENT("Apartment"),
    HOUSE("House"),
    VILLA("Villa"),
    SHOP("Shop"),
    OFFICE("Office"),
    LAND("Land"),
    OTHER("Other");

    private final String displayLabel;

    PropertyType(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    @Override
    public String toString() {
        return displayLabel;
    }
}
