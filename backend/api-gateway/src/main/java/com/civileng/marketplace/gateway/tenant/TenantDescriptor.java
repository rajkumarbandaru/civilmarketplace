package com.civileng.marketplace.gateway.tenant;

import lombok.Data;

import java.util.Set;

/** What tenant-service tells the gateway about a host. */
@Data
public class TenantDescriptor {

    private String tenantKey;
    private String name;
    private String subdomain;
    private String customDomain;
    private String status;
    private String vertical;
    private Set<String> modules;

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    /** Live but read-only for a few seconds while its data is moved or restored. */
    public boolean isMaintenance() {
        return "MAINTENANCE".equals(status);
    }

    public boolean hasModule(String moduleKey) {
        return modules != null && modules.contains(moduleKey);
    }
}
