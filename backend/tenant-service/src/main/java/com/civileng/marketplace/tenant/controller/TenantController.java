package com.civileng.marketplace.tenant.controller;

import com.civileng.marketplace.tenant.common.TenantMenuOverride;
import com.civileng.marketplace.tenant.dto.CreateTenantRequest;
import com.civileng.marketplace.tenant.dto.UpdateTenantRequest;
import com.civileng.marketplace.tenant.dto.TenantResponse;
import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.model.TenantStatus;
import com.civileng.marketplace.tenant.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tenant administration. Every write here is operator-level: it provisions or withdraws schemas
 * across the whole platform, so it is gated on the operator tenant, not merely on an admin role
 * — a tenant's own SUPER_ADMIN must not be able to create or suspend a sibling tenant.
 */
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private static final String OPERATOR_TENANT = "platform";

    private final TenantService tenantService;

    @PostMapping
    public ResponseEntity<TenantResponse> create(
            @Valid @RequestBody CreateTenantRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        requireOperator(role, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(withOverrides(tenantService.create(request, userId)));
    }

    /**
     * Changes a tenant's identity — name, subdomain, custom domain, contact, plan, vertical.
     *
     * <p>Modules, navigation and branding each have their own endpoint below. They are separate
     * because each carries a different warning the console has to show first, not because the
     * console shows them on different screens.
     */
    @PutMapping("/{tenantKey}")
    public ResponseEntity<TenantResponse> update(
            @PathVariable String tenantKey,
            @Valid @RequestBody UpdateTenantRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        requireOperator(role, tenantId);
        return ResponseEntity.ok(
                withOverrides(tenantService.update(tenantKey, request, userId)));
    }

    @GetMapping
    public ResponseEntity<List<TenantResponse>> list(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        requireOperator(role, tenantId);
        return ResponseEntity.ok(tenantService.listAll().stream()
                .map(TenantResponse::from)
                .toList());
    }

    @GetMapping("/{tenantKey}")
    public ResponseEntity<TenantResponse> get(
            @PathVariable String tenantKey,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        // A tenant may read itself; only the operator may read any other.
        if (!tenantKey.equals(tenantId)) {
            requireOperator(role, tenantId);
        }
        return ResponseEntity.ok(withOverrides(tenantService.byKey(tenantKey)));
    }

    @PatchMapping("/{tenantKey}/status")
    public ResponseEntity<TenantResponse> changeStatus(
            @PathVariable String tenantKey,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        requireOperator(role, tenantId);
        TenantStatus status = TenantStatus.valueOf(body.get("status").toUpperCase());
        return ResponseEntity.ok(
                withOverrides(tenantService.changeStatus(tenantKey, status, userId)));
    }

    /**
     * Replaces the tenant's navigation: hidden items, renamed labels, order, and landing page.
     *
     * <p>Supersedes the old {@code PUT /menu}, which took a list of keys to hide and so could not
     * express the two things operators actually wanted — reordering the sidebar and renaming a tab
     * to the customer's own vocabulary.
     */
    @PutMapping("/{tenantKey}/navigation")
    public ResponseEntity<TenantResponse> setNavigation(
            @PathVariable String tenantKey,
            @RequestBody NavigationRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        requireOperator(role, tenantId);
        return ResponseEntity.ok(withOverrides(tenantService.setNavigation(
                tenantKey, body.menuOverrides(), body.landingPath(), body.landingItemKey(),
                userId)));
    }

    /**
     * The navigation payload. A record rather than a {@code Map<String, Object>} because it carries
     * two differently-shaped fields, and the map version would have needed an unchecked cast to read
     * either of them.
     */
    public record NavigationRequest(List<TenantMenuOverride> menuOverrides, String landingPath,
                                    /**
                                     * The catalogue key of the landing page, sent for validation
                                     * only and never stored. The shell needs a path; checking the
                                     * choice against the items being hidden needs a key, and the
                                     * catalogue that maps one to the other lives in admin-service.
                                     */
                                    String landingItemKey) {
    }

    @PutMapping("/{tenantKey}/branding")
    public ResponseEntity<TenantResponse> setBranding(
            @PathVariable String tenantKey,
            @RequestBody com.civileng.marketplace.tenant.common.TenantBranding branding,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        requireOperator(role, tenantId);
        return ResponseEntity.ok(withOverrides(
                tenantService.updateBranding(tenantKey, branding, userId)));
    }

    @PutMapping("/{tenantKey}/modules")
    public ResponseEntity<TenantResponse> setModules(
            @PathVariable String tenantKey,
            @RequestBody Map<String, Set<String>> body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        requireOperator(role, tenantId);
        return ResponseEntity.ok(withOverrides(
                tenantService.setModules(tenantKey, body.get("modules"), userId)));
    }

    /**
     * A single tenant with its menu overrides attached.
     *
     * <p>The list endpoint deliberately does not do this: overrides are a second query per tenant
     * and the list does not render them, so paying for it there would put N queries behind a screen
     * that shows none of the answers.
     */
    private TenantResponse withOverrides(Tenant tenant) {
        return TenantResponse.from(tenant, tenantService.menuOverrides(tenant.getTenantKey()));
    }

    private void requireOperator(String role, String tenantId) {
        if (!OPERATOR_TENANT.equals(tenantId) || !"SUPER_ADMIN".equals(role)) {
            throw new com.civileng.marketplace.web.common.AccessDeniedException(
                    "Tenant administration is restricted to the operator tenant's SUPER_ADMINs");
        }
    }
}
