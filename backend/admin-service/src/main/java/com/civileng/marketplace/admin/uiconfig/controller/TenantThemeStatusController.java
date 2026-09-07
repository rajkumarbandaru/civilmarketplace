package com.civileng.marketplace.admin.uiconfig.controller;

import com.civileng.marketplace.admin.uiconfig.model.ThemeConfig;
import com.civileng.marketplace.admin.uiconfig.repository.ThemeConfigRepository;
import com.civileng.marketplace.tenant.common.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.civileng.marketplace.web.common.AccessDeniedException;

/**
 * Answers one question for the operator console: has this tenant customised its own theme?
 *
 * <p>The console needs it before offering to overwrite a tenant's branding — replacing colours a
 * tenant chose for themselves is a different act from filling in a default nobody has touched, and
 * the operator should be told which one they are about to do.
 *
 * <p>Reading another tenant's schema is exactly what the platform otherwise forbids, so the guard
 * here is the same one tenant-service uses: SUPER_ADMIN of the {@code platform} tenant, no one
 * else. Only the version and a boolean are returned — never the tenant's actual colours, which the
 * operator has no business reading.
 */
@RestController
@RequestMapping("/api/v1/admin/tenant-theme")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "platform.tenant", name = "enabled", havingValue = "true")
public class TenantThemeStatusController {

    private static final String OPERATOR_TENANT = "platform";

    private final ThemeConfigRepository themeRepository;

    /** @param customised true once the tenant has saved its own theme at least once. */
    public record TenantThemeStatus(String tenantKey, int version, boolean customised) {
    }

    @GetMapping("/{tenantKey}")
    @Operation(summary = "Whether a tenant has customised its own theme (operator only)")
    public ResponseEntity<TenantThemeStatus> status(
            @PathVariable String tenantKey,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        if (!OPERATOR_TENANT.equals(tenantId) || !"SUPER_ADMIN".equals(role)) {
            // The service's own exception, not ResponseStatusException — GlobalExceptionHandler
            // maps this one to 403 and everything it does not recognise to 500.
            throw new AccessDeniedException(
                    "Only a Super Admin of the platform tenant may read another tenant's theme state");
        }

        int version = TenantContext.callAs(tenantKey, () -> themeRepository
                .findById(ThemeConfig.PLATFORM_SCOPE)
                .map(ThemeConfig::getVersion)
                .orElse(0));

        // Version 1 is the row Flyway seeds for every tenant; anything above it means a save.
        return ResponseEntity.ok(new TenantThemeStatus(tenantKey, version, version > 1));
    }
}
