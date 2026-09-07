package com.civileng.marketplace.tenant.common;

import lombok.RequiredArgsConstructor;

/** Maps a tenant key to this service's schema for that tenant. */
@RequiredArgsConstructor
public class TenantSchemas {

    private final String prefix;

    public String schemaFor(String tenantKey) {
        return prefix + "_" + TenantKey.normalise(tenantKey);
    }

    public String prefix() {
        return prefix;
    }
}
