package com.civileng.marketplace.tenant.controller;

import com.civileng.marketplace.tenant.dto.IntegrationDtos.CapabilityView;
import com.civileng.marketplace.tenant.dto.IntegrationDtos.IntegrationView;
import com.civileng.marketplace.tenant.dto.IntegrationDtos.SaveIntegrationRequest;
import com.civileng.marketplace.tenant.entitlement.EntitlementService;
import com.civileng.marketplace.tenant.entitlement.FeatureCatalog;
import com.civileng.marketplace.tenant.model.PlatformModule;
import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.service.TenantIntegrationService;
import com.civileng.marketplace.tenant.service.TenantService;
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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A workspace's own settings, run by its own admins: which modules it runs (within its plan) and
 * which provider accounts it uses. Always the caller's own tenant — taken from the signed
 * {@code X-Tenant-Id}, never from the path — so one workspace can never read or change another's.
 */
@RestController
@RequestMapping("/api/v1/workspace-settings")
@RequiredArgsConstructor
public class WorkspaceSettingsController {

    static final String OPERATOR_TENANT = "platform";
    static final Set<String> ADMIN_ROLES = Set.of("SUPER_ADMIN", "ADMIN");

    /** Modules a workspace cannot switch off: without them nobody could sign in or manage it. */
    static final Set<String> LOCKED = Set.of("auth", "users", "admin", "audit");

    private final TenantService tenantService;
    private final EntitlementService entitlements;
    private final TenantIntegrationService integrations;

    /** One module as the settings screen shows it. */
    public record ModuleView(String key, boolean chosen, boolean running, boolean entitled, boolean locked) { }

    public record ModulesView(String tenantKey, String planName, List<ModuleView> modules) { }

    public record ModulesRequest(Set<String> modules) { }

    @GetMapping("/modules")
    public ResponseEntity<ModulesView> modules(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        return ResponseEntity.ok(view(tenantService.byKey(requireWorkspaceAdmin(role, tenantId))));
    }

    /**
     * Replaces the optional modules the workspace runs. Locked modules are kept whatever is sent,
     * and anything outside the plan is refused by the same rule the operator console follows.
     */
    @PutMapping("/modules")
    public ResponseEntity<ModulesView> setModules(
            @RequestBody ModulesRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        String tenantKey = requireWorkspaceAdmin(role, tenantId);
        Set<String> requested = request == null || request.modules() == null ? Set.of() : request.modules();
        if (requested.contains(FeatureCatalog.OPERATOR_ONLY)) {
            throw new IllegalArgumentException("Tenant administration is not a workspace module");
        }
        Set<String> modules = new LinkedHashSet<>(LOCKED);
        modules.addAll(requested);
        return ResponseEntity.ok(view(tenantService.setModules(tenantKey, modules, userId)));
    }

    @GetMapping("/integration-catalog")
    public ResponseEntity<List<CapabilityView>> catalog(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        requireWorkspaceAdmin(role, tenantId);
        return ResponseEntity.ok(integrations.catalog());
    }

    @GetMapping("/integrations")
    public ResponseEntity<List<IntegrationView>> integrations(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        return ResponseEntity.ok(integrations.list(requireWorkspaceAdmin(role, tenantId)));
    }

    @PutMapping("/integrations/{capability}")
    public ResponseEntity<IntegrationView> saveIntegration(
            @PathVariable String capability,
            @RequestBody SaveIntegrationRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        return ResponseEntity.ok(integrations.save(requireWorkspaceAdmin(role, tenantId), capability, request, userId));
    }

    /** Back to the platform's account. */
    @DeleteMapping("/integrations/{capability}")
    public ResponseEntity<Void> resetIntegration(
            @PathVariable String capability,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        String tenantKey = requireWorkspaceAdmin(role, tenantId);
        try {
            integrations.delete(tenantKey, capability, userId);
        } catch (java.util.NoSuchElementException alreadyOnPlatform) {
            // Nothing of its own was stored: it is on the platform's account already.
        }
        return ResponseEntity.noContent().build();
    }

    private ModulesView view(Tenant tenant) {
        EntitlementService.Entitlements e = entitlements.of(tenant.getTenantKey());
        Set<String> chosen = tenant.moduleKeys();
        List<ModuleView> modules = Arrays.stream(PlatformModule.values())
                .map(PlatformModule::key)
                .filter(key -> !FeatureCatalog.OPERATOR_ONLY.equals(key))
                .map(key -> new ModuleView(key, chosen.contains(key), chosen.contains(key) && e.has(key),
                        e.has(key), LOCKED.contains(key)))
                .toList();
        return new ModulesView(tenant.getTenantKey(), e.planName(), modules);
    }

    /** The caller's own customer workspace; its admins only. */
    static String requireWorkspaceAdmin(String role, String tenantId) {
        if (tenantId == null || tenantId.isBlank() || !ADMIN_ROLES.contains(role)) {
            throw new AccessDeniedException("Workspace settings are for this workspace's admins");
        }
        if (OPERATOR_TENANT.equals(tenantId)) {
            throw new IllegalArgumentException("The operator workspace is configured by the platform itself");
        }
        return tenantId;
    }
}
