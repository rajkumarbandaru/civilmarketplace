package com.civileng.marketplace.web.common;

import java.util.Set;

/**
 * The staff roles seeded by auth-service's {@code roles} table.
 *
 * <p>One definition, because the set was previously written out by hand in every controller that
 * gated on it — and a role added to the seed data would have had to be found in each of them.
 */
public final class StaffRoles {

    public static final Set<String> ALL =
            Set.of("SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN");

    private StaffRoles() {
    }

    public static boolean isStaff(String role) {
        return role != null && ALL.contains(role);
    }

    /** Throws {@link AccessDeniedException} unless {@code role} is a staff role. */
    public static void requireStaff(String role) {
        if (!isStaff(role)) {
            throw new AccessDeniedException("Admin role required");
        }
    }
}
