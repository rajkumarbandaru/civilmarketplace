package com.civileng.marketplace.tenant.common;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * Carries the caller's tenant on outbound Feign calls.
 *
 * <p>Service-to-service calls go direct through Eureka, not back through the gateway, so nothing
 * else would set {@code X-Tenant-Id} on them. Without this a Feign call from one tenanted service
 * to another is rejected by the callee's tenant filter — or worse, if it were defaulted, served
 * from the wrong schema.
 */
public class TenantFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String tenantId = TenantContext.get();
        if (tenantId != null) {
            template.header(TenantHeaderFilter.TENANT_HEADER, tenantId);
        }
    }
}
