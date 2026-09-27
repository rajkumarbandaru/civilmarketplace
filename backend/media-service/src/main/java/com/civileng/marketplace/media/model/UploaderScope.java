package com.civileng.marketplace.media.model;

import com.civileng.marketplace.web.common.PlatformRoles;
import com.civileng.marketplace.web.common.StaffRoles;

/** Who may start an upload for a given purpose. */
public enum UploaderScope {
    ANY_USER,
    STAFF,
    /**
     * A workspace owner, or a platform admin — who sets a new tenant's logo in the factory before
     * that tenant has an owner of its own.
     */
    OWNER;

    public boolean allows(String role) {
        return switch (this) {
            case ANY_USER -> true;
            case STAFF -> StaffRoles.isStaff(role);
            case OWNER -> StaffRoles.isOwner(role) || PlatformRoles.ADMIN.equals(role);
        };
    }
}
