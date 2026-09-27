package com.civileng.marketplace.admin.config;

import com.civileng.marketplace.admin.uiconfig.service.RoleDirectory;
import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.audit.common.AuditEventMessage;
import com.civileng.marketplace.audit.common.AuditPublisher;
import com.civileng.marketplace.web.common.AccessDeniedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * A workspace's theme history: what was published when, what each release changed, and going
 * back. Same guard as editing the theme — this workspace's owner.
 */
@RestController
@RequestMapping("/api/v1/admin/config")
@RequiredArgsConstructor
@Tag(name = "Configuration history", description = "Versions, diff and rollback of theme documents")
public class ConfigHistoryController {

    private final ConfigService configService;
    private final RoleDirectory roleDirectory;
    private final AuditPublisher auditPublisher;
    private final ThemePreviewTokens previewTokens;
    private final com.civileng.marketplace.admin.uiconfig.service.UiConfigService uiConfigService;

    public record RollbackRequest(String reason) { }

    public record PreviewResponse(String token, String path, long expiresInSeconds) { }

    /** @param scope {@code PLATFORM} for the whole workspace, or a role name */
    @GetMapping("/releases")
    @Operation(summary = "Published releases for a scope, newest first")
    public ResponseEntity<List<ConfigService.ReleaseView>> releases(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(defaultValue = "PLATFORM") String scope,
            @RequestParam(defaultValue = "50") int limit) {
        requireOwner(role);
        return ResponseEntity.ok(configService.history(scope(scope), limit));
    }

    @GetMapping("/releases/{releaseId}/diff")
    @Operation(summary = "What a release changed, key by key")
    public ResponseEntity<ConfigService.ReleaseDiff> diff(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long releaseId) {
        requireOwner(role);
        return ResponseEntity.ok(configService.diff(releaseId));
    }

    @PostMapping("/releases/{releaseId}/rollback")
    @Operation(summary = "Publish again what was live after this release (re-validated)")
    public ResponseEntity<ConfigService.ReleaseView> rollback(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @PathVariable Long releaseId,
            @RequestBody(required = false) RollbackRequest request) {
        requireOwner(role);
        String reason = request == null ? null : request.reason();
        ConfigService.ReleaseView release = configService.rollback(releaseId, reason, adminId);
        auditPublisher.publish(AuditEventMessage.builder()
                .sourceService("admin-service")
                .actorId(adminId)
                .actorRole(role)
                .action(AuditAction.UPDATE)
                .entityType("ConfigRelease")
                .entityId(String.valueOf(release.id()))
                .beforeState("rollbackOf=" + releaseId)
                .afterState("documents=" + release.documents())
                .reason(reason)
                .build());
        return ResponseEntity.ok(release);
    }

    /**
     * A 15-minute link that opens the real app with this unpublished theme. Checked like a save
     * first, so a preview can never show something that could not be published.
     */
    @PostMapping("/preview")
    @Operation(summary = "Issue a preview link for an unpublished theme")
    public ResponseEntity<PreviewResponse> preview(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @RequestBody com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.ThemeUpdateCommand values) {
        requireOwner(role);
        uiConfigService.previewTheme(values);
        String token = previewTokens.issue(com.civileng.marketplace.tenant.common.TenantContext.require(), adminId, values);
        return ResponseEntity.ok(new PreviewResponse(token, "/?preview=" + token, ThemePreviewTokens.TTL.toSeconds()));
    }

    private ConfigScope scope(String scope) {
        if ("PLATFORM".equals(scope)) return ConfigScope.TENANT;
        if (!roleDirectory.exists(scope)) throw new IllegalArgumentException("No such workspace: " + scope);
        return ConfigScope.role(scope);
    }

    private static void requireOwner(String role) {
        if (!com.civileng.marketplace.web.common.StaffRoles.isOwner(role)) {
            throw new AccessDeniedException("Workspace owner role required to view or roll back the theme");
        }
    }
}
