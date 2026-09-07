package com.civileng.marketplace.tenant.common;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;

import java.util.Map;

/**
 * {@code /actuator/tenants} — which tenants this service instance has provisioned, and a manual
 * re-provision for the case where a {@code tenant.events} message was missed while the service
 * was down and a restart is not wanted.
 */
@Endpoint(id = "tenants")
@RequiredArgsConstructor
public class TenantAdminEndpoint {

    private final TenantSchemaBootstrap bootstrap;
    private final TenantSchemaMigrator migrator;

    @ReadOperation
    public Map<String, Object> tenants() {
        return Map.of(
                "bootstrapTenant", bootstrap.bootstrapTenant(),
                "provisioned", bootstrap.tenantKeys());
    }

    @WriteOperation
    public Map<String, Object> provision(String tenantKey) {
        migrator.migrate(tenantKey);
        return Map.of("tenantKey", tenantKey, "status", "provisioned");
    }
}
