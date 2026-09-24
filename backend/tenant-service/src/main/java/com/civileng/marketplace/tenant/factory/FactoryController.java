package com.civileng.marketplace.tenant.factory;

import com.civileng.marketplace.tenant.dto.TenantResponse;
import com.civileng.marketplace.tenant.model.TenantStatusChange;
import com.civileng.marketplace.tenant.repository.TenantStatusChangeRepository;
import com.civileng.marketplace.tenant.service.TenantService;
import com.civileng.marketplace.web.common.AccessDeniedException;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The Platform Factory API: wizard drafts, publishing and provisioning progress. Operator-only,
 * like all of tenant administration — SUPER_ADMIN of the platform tenant.
 */
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "Platform Factory", description = "Drafts, publish, provisioning and lifecycle of tenants")
public class FactoryController {

    private final DraftService drafts;
    private final ProvisioningService provisioning;
    private final TenantService tenantService;
    private final TenantStatusChangeRepository history;

    public record SaveDraftRequest(int version, JsonNode data) { }

    @GetMapping("/drafts")
    @Operation(summary = "Open wizard drafts, most recently edited first")
    public ResponseEntity<List<DraftService.DraftView>> drafts(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                               @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return ResponseEntity.ok(drafts.open());
    }

    @PostMapping("/drafts")
    public ResponseEntity<DraftService.DraftView> newDraft(@RequestBody JsonNode data,
                                                           @RequestHeader(value = "X-User-Id", required = false) String actor,
                                                           @RequestHeader(value = "X-User-Role", required = false) String role,
                                                           @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return ResponseEntity.status(HttpStatus.CREATED).body(drafts.create(data, actor));
    }

    @GetMapping("/drafts/{id}")
    public ResponseEntity<DraftService.DraftView> draft(@PathVariable Long id,
                                                        @RequestHeader(value = "X-User-Role", required = false) String role,
                                                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return ResponseEntity.ok(drafts.get(id));
    }

    @PutMapping("/drafts/{id}")
    @Operation(summary = "Autosave a draft; 409 if someone else saved it since your version")
    public ResponseEntity<DraftService.DraftView> saveDraft(@PathVariable Long id, @RequestBody SaveDraftRequest request,
                                                            @RequestHeader(value = "X-User-Id", required = false) String actor,
                                                            @RequestHeader(value = "X-User-Role", required = false) String role,
                                                            @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return ResponseEntity.ok(drafts.save(id, request.version(), request.data(), actor));
    }

    @DeleteMapping("/drafts/{id}")
    public ResponseEntity<Void> discardDraft(@PathVariable Long id,
                                             @RequestHeader(value = "X-User-Role", required = false) String role,
                                             @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        drafts.discard(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/drafts/{id}/create")
    @Operation(summary = "Turn a draft into a DRAFT tenant: reserves key and subdomain, provisions nothing")
    public ResponseEntity<TenantResponse> createFromDraft(@PathVariable Long id,
                                                          @RequestHeader(value = "X-User-Id", required = false) String actor,
                                                          @RequestHeader(value = "X-User-Role", required = false) String role,
                                                          @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        var created = drafts.createTenant(id, actor);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TenantResponse.from(created, tenantService.menuOverrides(created.getTenantKey())));
    }

    @PostMapping("/{tenantKey}/publish")
    @Operation(summary = "Publish a DRAFT (or retry a failed) tenant: provision everywhere, then go live and invite the owner")
    public ResponseEntity<ProvisioningService.ProvisioningView> publish(@PathVariable String tenantKey,
                                                                        @RequestHeader(value = "X-User-Id", required = false) String actor,
                                                                        @RequestHeader(value = "X-User-Role", required = false) String role,
                                                                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return ResponseEntity.accepted().body(provisioning.publish(tenantKey, actor));
    }

    @GetMapping("/{tenantKey}/provisioning")
    @Operation(summary = "Provisioning progress: saga step and each service's acknowledgement")
    public ResponseEntity<ProvisioningService.ProvisioningView> progress(@PathVariable String tenantKey,
                                                                         @RequestHeader(value = "X-User-Role", required = false) String role,
                                                                         @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return ResponseEntity.ok(provisioning.view(tenantKey));
    }

    @PostMapping("/{tenantKey}/owner-invitation")
    @Operation(summary = "Send the owner a fresh invitation (the previous link stops working)")
    public ResponseEntity<Void> resendInvitation(@PathVariable String tenantKey,
                                                 @RequestHeader(value = "X-User-Id", required = false) String actor,
                                                 @RequestHeader(value = "X-User-Role", required = false) String role,
                                                 @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        provisioning.resendInvitation(tenantKey, actor);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{tenantKey}")
    @Operation(summary = "Discard a tenant that never went live (DRAFT or PROVISIONING_FAILED)")
    public ResponseEntity<Void> discard(@PathVariable String tenantKey,
                                        @RequestHeader(value = "X-User-Id", required = false) String actor,
                                        @RequestHeader(value = "X-User-Role", required = false) String role,
                                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        tenantService.discard(tenantKey, actor);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{tenantKey}/history")
    @Operation(summary = "Lifecycle transitions, newest first")
    public ResponseEntity<List<TenantStatusChange>> history(@PathVariable String tenantKey,
                                                            @RequestHeader(value = "X-User-Role", required = false) String role,
                                                            @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return ResponseEntity.ok(history.findByTenantKeyOrderByIdDesc(tenantKey));
    }

    private static void requireOperator(String role, String tenantId) {
        if (!"platform".equals(tenantId) || !"SUPER_ADMIN".equals(role)) {
            throw new AccessDeniedException("Tenant administration is restricted to the operator tenant's SUPER_ADMINs");
        }
    }
}
