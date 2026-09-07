package com.civileng.marketplace.tenant.dto;

import com.civileng.marketplace.tenant.common.TenantBranding;
import com.civileng.marketplace.tenant.common.TenantMenuOverride;
import com.civileng.marketplace.tenant.model.Tenant;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@Builder
public class TenantResponse {

    private String tenantKey;
    private String name;
    private String subdomain;
    private String customDomain;
    private String status;
    private String contactEmail;
    private String plan;
    private String vertical;
    private Set<String> modules;
    private List<TenantMenuOverride> menuOverrides;
    private String landingPath;
    private TenantBranding branding;
    private LocalDateTime createdAt;

    /**
     * @param menuOverrides read separately — they live in their own table, not on the tenant row,
     *                      so a caller that does not need them (the gateway's host resolution, the
     *                      tenant list) is not made to pay for the query. Null renders as absent.
     */
    public static TenantResponse from(Tenant tenant, List<TenantMenuOverride> menuOverrides) {
        return TenantResponse.builder()
                .tenantKey(tenant.getTenantKey())
                .name(tenant.getName())
                .subdomain(tenant.getSubdomain())
                .customDomain(tenant.getCustomDomain())
                .status(tenant.getStatus().name())
                .contactEmail(tenant.getContactEmail())
                .plan(tenant.getPlan())
                .vertical(tenant.getVertical().name())
                .modules(tenant.moduleKeys())
                .menuOverrides(menuOverrides)
                .landingPath(tenant.getLandingPath())
                .branding(tenant.branding())
                .createdAt(tenant.getCreatedAt())
                .build();
    }

    public static TenantResponse from(Tenant tenant) {
        return from(tenant, null);
    }
}
