package com.civileng.marketplace.tenant.controller;

import java.util.NoSuchElementException;
import com.civileng.marketplace.tenant.dto.TenantResponse;
import com.civileng.marketplace.tenant.entitlement.EntitlementService;
import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Host → tenant resolution, called by the gateway on every request it cannot answer from its
 * cache and by the frontend before login (it needs the tenant's branding on the login screen,
 * where no JWT exists yet).
 *
 * <p>Unauthenticated by design — it exposes nothing a visitor cannot already infer from the URL
 * they typed. It returns only public identity, never contact or plan details.
 */
@RestController
@RequestMapping("/api/v1/tenant-resolution")
@RequiredArgsConstructor
public class TenantResolutionController {

    private final TenantService tenantService;
    private final EntitlementService entitlements;

    @GetMapping
    public ResponseEntity<TenantResponse> resolve(@RequestParam String host) {
        Tenant tenant = tenantService.byHost(host);

        return ResponseEntity.ok(TenantResponse.builder()
                .tenantKey(tenant.getTenantKey())
                .name(tenant.getName())
                .subdomain(tenant.getSubdomain())
                .customDomain(tenant.getCustomDomain())
                .status(tenant.getStatus().name())
                .vertical(tenant.getVertical().name())
                .modules(entitlements.runningModules(tenant))
                .build());
    }

    /**
     * The workspace this request was addressed to, as the gateway resolved it from the Host —
     * what the frontend loads before sign-in to brand the login screen. The header is trusted
     * because it is signed (InternalContextFilter refuses it otherwise). Public identity and
     * branding only.
     */
    @GetMapping("/current")
    public ResponseEntity<TenantResponse> current(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new NoSuchElementException("No workspace on this request");
        }
        Tenant tenant = tenantService.byKey(tenantId);
        return ResponseEntity.ok(TenantResponse.builder()
                .tenantKey(tenant.getTenantKey())
                .name(tenant.getName())
                .status(tenant.getStatus().name())
                .vertical(tenant.getVertical().name())
                .modules(entitlements.runningModules(tenant))
                .branding(tenant.branding())
                .build());
    }
}
