package com.civileng.marketplace.web.common.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;
import java.util.Set;

/**
 * The calling tenant's entitlements from tenant-service (the tenant rides the signed X-Tenant-Id
 * the tenant Feign interceptor adds). Behind {@code /api/v1/tenants/internal}, sealed at the gateway.
 */
@FeignClient(name = "tenant-service", contextId = "entitlementsClient", path = "/api/v1/tenants/internal")
public interface EntitlementsClient {

    record Entitlements(String tenantKey, String planKey, String planName, String status, Set<String> features,
                        Map<String, Long> limits) { }

    @GetMapping("/entitlements")
    Entitlements mine();
}
