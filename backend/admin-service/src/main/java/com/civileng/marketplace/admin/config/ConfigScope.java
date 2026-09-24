package com.civileng.marketplace.admin.config;

/**
 * Where a document is set: the whole workspace ({@code TENANT}) or one role's workspace view
 * ({@code ROLE:<role>}). Stored as that string.
 */
public record ConfigScope(ConfigDocument.Level level, String role) {

    public static final ConfigScope TENANT = new ConfigScope(ConfigDocument.Level.TENANT, null);

    public static ConfigScope role(String role) {
        return new ConfigScope(ConfigDocument.Level.ROLE, role);
    }

    /** The console's existing scope keys: {@code PLATFORM} for the workspace, else a role name. */
    public static ConfigScope fromThemeScopeKey(String scopeKey) {
        return "PLATFORM".equals(scopeKey) ? TENANT : role(scopeKey);
    }

    public static ConfigScope parse(String stored) {
        if ("TENANT".equals(stored)) return TENANT;
        if (stored != null && stored.startsWith("ROLE:")) return role(stored.substring(5));
        throw new IllegalArgumentException("Unknown configuration scope '" + stored + "'");
    }

    public String key() {
        return level == ConfigDocument.Level.TENANT ? "TENANT" : "ROLE:" + role;
    }

    @Override
    public String toString() {
        return key();
    }
}
