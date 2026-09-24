package com.civileng.marketplace.tenant.entitlement;

import com.civileng.marketplace.tenant.service.TenantService;
import com.civileng.marketplace.web.common.AccessDeniedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Plans, subscriptions and grants. The operator endpoints are for the platform's Super Admins; the
 * {@code /internal} one is for services asking about their own current tenant (the gateway never
 * routes it from outside).
 */
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "Entitlements", description = "Plans, subscriptions, add-ons, grants and limits")
public class EntitlementController {

    private final EntitlementService entitlements;
    private final SubscriptionService subscriptions;
    private final TenantService tenantService;

    public record Catalog(List<EntitlementService.PlanView> plans, Collection<FeatureCatalog.AddOn> addOns,
                          Map<String, String> limits, Set<String> baseModules) { }

    public record TenantEntitlements(EntitlementService.Entitlements entitlements, Set<String> chosenModules,
                                     Set<String> runningModules) { }

    public record ChangeRequest(String plan, List<String> addOns) { }

    public record StatusRequest(TenantSubscription.Status status) { }

    public record GrantRequest(String feature, Long limitValue, LocalDateTime expiresAt, String reason) { }

    @GetMapping("/plans")
    @Operation(summary = "Plans (latest versions), add-ons and limits on offer")
    public ResponseEntity<Catalog> catalog(@RequestHeader(value = "X-User-Role", required = false) String role,
                                           @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return ResponseEntity.ok(new Catalog(entitlements.catalog(), FeatureCatalog.ADD_ONS.values(),
                FeatureCatalog.LIMITS, new TreeSet<>(FeatureCatalog.BASE)));
    }

    @GetMapping("/{tenantKey}/entitlements")
    @Operation(summary = "What a tenant may use, what it chose, and what therefore runs")
    public ResponseEntity<TenantEntitlements> ofTenant(@PathVariable String tenantKey,
                                                       @RequestHeader(value = "X-User-Role", required = false) String role,
                                                       @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        var t = tenantService.byKey(tenantKey);
        var e = entitlements.of(tenantKey);
        return ResponseEntity.ok(new TenantEntitlements(e, t.moduleKeys(), EntitlementService.running(t.moduleKeys(), e)));
    }

    @GetMapping("/{tenantKey}/subscription/preview")
    @Operation(summary = "What a plan or add-on change would switch off or back on, before making it")
    public ResponseEntity<SubscriptionService.Impact> preview(@PathVariable String tenantKey, @RequestParam String plan,
                                                              @RequestParam(required = false) List<String> addOns,
                                                              @RequestHeader(value = "X-User-Role", required = false) String role,
                                                              @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return ResponseEntity.ok(subscriptions.preview(tenantKey, plan, addOns));
    }

    @PutMapping("/{tenantKey}/subscription")
    @Operation(summary = "Change plan and add-ons (modules outside the new entitlement stop; their data is kept)")
    public ResponseEntity<EntitlementService.Entitlements> change(@PathVariable String tenantKey, @RequestBody ChangeRequest request,
                                                                  @RequestHeader(value = "X-User-Id", required = false) String actor,
                                                                  @RequestHeader(value = "X-User-Role", required = false) String role,
                                                                  @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return ResponseEntity.ok(subscriptions.change(tenantKey, request.plan(), request.addOns(), actor));
    }

    @PutMapping("/{tenantKey}/subscription/status")
    public ResponseEntity<EntitlementService.Entitlements> status(@PathVariable String tenantKey, @RequestBody StatusRequest request,
                                                                  @RequestHeader(value = "X-User-Id", required = false) String actor,
                                                                  @RequestHeader(value = "X-User-Role", required = false) String role,
                                                                  @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return ResponseEntity.ok(subscriptions.setStatus(tenantKey, request.status(), actor));
    }

    @PostMapping("/{tenantKey}/grants")
    @Operation(summary = "Grant a feature or raise a limit until a date (at most 12 months)")
    public ResponseEntity<EntitlementService.Entitlements> grant(@PathVariable String tenantKey, @RequestBody GrantRequest request,
                                                                 @RequestHeader(value = "X-User-Id", required = false) String actor,
                                                                 @RequestHeader(value = "X-User-Role", required = false) String role,
                                                                 @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return ResponseEntity.ok(subscriptions.grant(tenantKey, request.feature(), request.limitValue(),
                request.expiresAt(), request.reason(), actor));
    }

    @DeleteMapping("/{tenantKey}/grants/{grantId}")
    public ResponseEntity<EntitlementService.Entitlements> revoke(@PathVariable String tenantKey, @PathVariable Long grantId,
                                                                  @RequestHeader(value = "X-User-Id", required = false) String actor,
                                                                  @RequestHeader(value = "X-User-Role", required = false) String role,
                                                                  @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return ResponseEntity.ok(subscriptions.revokeGrant(tenantKey, grantId, actor));
    }

    /**
     * For services: the calling tenant's entitlements (limits above all). The tenant comes from the
     * signed X-Tenant-Id; the gateway refuses this prefix from outside.
     */
    @GetMapping("/internal/entitlements")
    public ResponseEntity<EntitlementService.Entitlements> mine(@RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        if (tenant == null || tenant.isBlank()) throw new IllegalArgumentException("No tenant on this request");
        return ResponseEntity.ok(entitlements.of(tenant));
    }

    private static void requireOperator(String role, String tenantId) {
        if (!"platform".equals(tenantId) || !"SUPER_ADMIN".equals(role)) {
            throw new AccessDeniedException("Plans and entitlements are managed by the operator tenant's SUPER_ADMINs");
        }
    }
}
