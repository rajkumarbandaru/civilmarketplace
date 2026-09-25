package com.civileng.marketplace.tenant.controller;

import com.civileng.marketplace.tenant.dto.IntegrationDtos.CapabilityView;
import com.civileng.marketplace.tenant.dto.IntegrationDtos.IntegrationView;
import com.civileng.marketplace.tenant.dto.IntegrationDtos.SaveIntegrationRequest;
import com.civileng.marketplace.tenant.service.TenantIntegrationService;
import com.civileng.marketplace.web.common.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Any tenant's provider accounts — payment gateway, mail, SMS, WhatsApp, AI — from the operator
 * console. A workspace's own admins manage theirs through {@link WorkspaceSettingsController}.
 *
 * <p>There is no endpoint that returns a secret, by construction: the response type has nowhere to
 * put one.
 */
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantIntegrationController {

    private static final String OPERATOR_TENANT = "platform";

    private final TenantIntegrationService integrationService;

    /** Which capabilities and providers exist, and which fields each needs — drives the form. */
    @GetMapping("/integration-catalog")
    public ResponseEntity<List<CapabilityView>> catalog(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        requireOperator(role, tenantId);
        return ResponseEntity.ok(integrationService.catalog());
    }

    @GetMapping("/{tenantKey}/integrations")
    public ResponseEntity<List<IntegrationView>> list(
            @PathVariable String tenantKey,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        requireOperator(role, tenantId);
        return ResponseEntity.ok(integrationService.list(tenantKey));
    }

    @PutMapping("/{tenantKey}/integrations/{capability}")
    public ResponseEntity<IntegrationView> save(
            @PathVariable String tenantKey,
            @PathVariable String capability,
            @RequestBody SaveIntegrationRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        requireOperator(role, tenantId);
        return ResponseEntity.ok(integrationService.save(tenantKey, capability, request, userId));
    }

    @DeleteMapping("/{tenantKey}/integrations/{capability}")
    public ResponseEntity<Void> delete(
            @PathVariable String tenantKey,
            @PathVariable String capability,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        requireOperator(role, tenantId);
        integrationService.delete(tenantKey, capability, userId);
        return ResponseEntity.noContent().build();
    }

    public record CryptoShredRequest(String confirm) { }

    /** Destroy an archived tenant's encryption keys in the secrets broker. Irreversible. */
    @org.springframework.web.bind.annotation.PostMapping("/{tenantKey}/crypto-shred")
    public ResponseEntity<com.civileng.marketplace.tenant.service.TenantIntegrationService.CryptoShredResult> cryptoShred(
            @PathVariable String tenantKey,
            @org.springframework.web.bind.annotation.RequestBody CryptoShredRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        requireOperator(role, tenantId);
        return ResponseEntity.ok(integrationService.cryptoShred(tenantKey, request == null ? null : request.confirm(), userId));
    }

    private void requireOperator(String role, String tenantId) {
        if (!OPERATOR_TENANT.equals(tenantId) || !"SUPER_ADMIN".equals(role)) {
            throw new AccessDeniedException(
                    "Tenant integrations are restricted to the operator tenant's SUPER_ADMINs");
        }
    }
}
