package com.civileng.marketplace.tenant.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "platform.tenant")
public class TenantProperties {

    /** Turns the whole schema-per-tenant runtime on. Off means the service runs single-schema. */
    private boolean enabled = true;

    /**
     * Schema prefix this service owns, e.g. {@code civil_engineer_users}. A tenant's schema is
     * {@code <prefix>_<tenantKey>}; the prefix alone is never used as a live schema once
     * multi-tenancy is on.
     */
    private String schemaPrefix;

    /** Migrations to run against every tenant schema. */
    private String migrationLocations = "classpath:db/migration";

    /** Paths served without a tenant (health, metrics, docs). */
    private String[] untenantedPaths = {
            "/actuator", "/api-docs", "/swagger-ui", "/webhooks"
    };

    private final Registry registry = new Registry();

    /**
     * The platform-level tenant registry, owned by tenant-service. Every service reads it
     * directly at boot to learn which schemas to migrate — an HTTP call would put tenant-service
     * in every other service's startup path.
     */
    @Data
    public static class Registry {
        private String url;
        private String username;
        private String password;
    }
}
