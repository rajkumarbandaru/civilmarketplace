package com.civileng.marketplace.user.model;

/**
 * The units a material can be priced in.
 *
 * <p>A closed set rather than free text, because these rates are read back by the estimator: "bag"
 * and "Bag" and "bags" as three separate strings would put three unrelated rows in one BOQ line.
 * The label is what a supplier and an estimate should show.
 */
public enum MaterialUnit {

    BAG("bag"),
    KG("kg"),
    QUINTAL("quintal"),
    TONNE("tonne"),
    CUBIC_FEET("cft"),
    CUBIC_METRE("m3"),
    SQUARE_FEET("sq.ft"),
    SQUARE_METRE("sq.m"),
    RUNNING_METRE("rmt"),
    LITRE("litre"),
    NUMBER("nos");

    private final String label;

    MaterialUnit(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
