package com.civileng.marketplace.media.model;

import com.civileng.marketplace.web.common.StaffRoles;

/** Who may start an upload for a given purpose. */
public enum UploaderScope {
    ANY_USER,
    STAFF,
    SUPER_ADMIN;

    public boolean allows(String role) {
        return switch (this) {
            case ANY_USER -> true;
            case STAFF -> StaffRoles.isStaff(role);
            case SUPER_ADMIN -> "SUPER_ADMIN".equals(role);
        };
    }
}
