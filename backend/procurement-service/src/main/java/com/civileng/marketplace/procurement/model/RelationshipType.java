package com.civileng.marketplace.procurement.model;

/** A buyer's standing with another organization. Preferred suppliers are listed first when inviting; blocked ones are excluded from matching either way. */
public enum RelationshipType {
    PREFERRED_SUPPLIER, BLOCKED
}
