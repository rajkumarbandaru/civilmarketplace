package com.civileng.marketplace.admin.uiconfig.controller;

import com.civileng.marketplace.admin.exception.AccessDeniedException;
import com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.*;
import com.civileng.marketplace.admin.uiconfig.service.UiConfigService;
import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.audit.common.AuditEventMessage;
import com.civileng.marketplace.audit.common.AuditPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Super Admin's view over the platform's look: the platform-wide theme, and every workspace's
 * menu and theme override. One role = one workspace.
 *
 * <p>Gated on SUPER_ADMIN alone, not the wider admin set — this changes what every user of the
 * platform sees, which is a different weight of decision from moderating one booking.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin UI Configuration", description = "Super Admin control of menus, themes and UI style")
public class AdminUiConfigController {

    private static final String PLATFORM = "PLATFORM";
    private static final String SOURCE = "admin-service";
    private static final String ENTITY = "UiTheme";

    private final UiConfigService uiConfigService;
    private final AuditPublisher auditPublisher;

    // ------------------------------------------------------------------- platform theme

    /** The platform-wide theme — the base every workspace inherits. Cannot be deleted. */
    @GetMapping("/theme")
    @Operation(summary = "Get the platform-wide theme and UI style")
    public ResponseEntity<ResolvedTheme> platformTheme(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireSuperAdmin(role);
        return ResponseEntity.ok(uiConfigService.theme(PLATFORM));
    }

    @PutMapping("/theme")
    @Operation(summary = "Save the platform-wide theme and UI style")
    public ResponseEntity<ResolvedTheme> updatePlatformTheme(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @RequestHeader(value = "X-User-Name", required = false) String adminName,
            @RequestBody ThemeUpdateCommand command) {
        requireSuperAdmin(role);
        log.info("Platform theme changed by {} (#{})", adminName, adminId);
        audit(adminId, "SUPER_ADMIN", PLATFORM, command);
        return ResponseEntity.ok(uiConfigService.updateTheme(PLATFORM, command));
    }

    /**
     * The shipped presets, offered on both theme screens. A GET rather than a bundled constant so
     * the console and any other client see the same list as the service that validates saves.
     */
    @GetMapping("/theme/presets")
    @Operation(summary = "Built-in theme presets a Super Admin can load into the form")
    public ResponseEntity<List<ThemePreset>> themePresets(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireSuperAdmin(role);
        return ResponseEntity.ok(uiConfigService.themePresets());
    }

    // ------------------------------------------------------------------- workspaces

    @GetMapping("/workspaces")
    @Operation(summary = "Every role's workspace, with its member count and whether it is customised")
    public ResponseEntity<List<WorkspaceSummary>> listWorkspaces(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireSuperAdmin(role);
        return ResponseEntity.ok(uiConfigService.listWorkspaces());
    }

    /** Every catalogue item with this workspace's overlay applied, hidden ones included. */
    @GetMapping("/workspaces/{workspaceRole}/menu")
    @Operation(summary = "One workspace's side menu, effective values plus catalogue defaults")
    public ResponseEntity<List<WorkspaceMenuRow>> workspaceMenu(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String workspaceRole) {
        requireSuperAdmin(role);
        return ResponseEntity.ok(uiConfigService.workspaceMenu(workspaceRole));
    }

    @PutMapping("/workspaces/{workspaceRole}/menu")
    @Operation(summary = "Save one workspace's side menu")
    public ResponseEntity<List<WorkspaceMenuRow>> updateWorkspaceMenu(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String workspaceRole,
            @RequestBody List<MenuUpdateCommand> commands) {
        requireSuperAdmin(role);
        uiConfigService.updateWorkspaceMenu(workspaceRole, commands);
        return ResponseEntity.ok(uiConfigService.workspaceMenu(workspaceRole));
    }

    @DeleteMapping("/workspaces/{workspaceRole}/menu")
    @Operation(summary = "Return one workspace's side menu to the catalogue defaults")
    public ResponseEntity<List<WorkspaceMenuRow>> resetWorkspaceMenu(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String workspaceRole) {
        requireSuperAdmin(role);
        uiConfigService.resetWorkspaceMenu(workspaceRole);
        return ResponseEntity.ok(uiConfigService.workspaceMenu(workspaceRole));
    }

    /** The stored row for this workspace only — nulls here mean "inherit the platform theme". */
    @GetMapping("/workspaces/{workspaceRole}/theme")
    @Operation(summary = "One workspace's own theme override, unmerged")
    public ResponseEntity<ResolvedTheme> workspaceTheme(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String workspaceRole) {
        requireSuperAdmin(role);
        return ResponseEntity.ok(uiConfigService.rawTheme(workspaceRole));
    }

    /** What this workspace actually renders, after the platform theme is merged underneath. */
    @GetMapping("/workspaces/{workspaceRole}/theme/effective")
    @Operation(summary = "One workspace's theme as it will actually be painted")
    public ResponseEntity<ResolvedTheme> effectiveWorkspaceTheme(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String workspaceRole) {
        requireSuperAdmin(role);
        return ResponseEntity.ok(uiConfigService.theme(workspaceRole));
    }

    @PutMapping("/workspaces/{workspaceRole}/theme")
    @Operation(summary = "Save one workspace's theme override")
    public ResponseEntity<ResolvedTheme> updateWorkspaceTheme(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @RequestHeader(value = "X-User-Name", required = false) String adminName,
            @PathVariable String workspaceRole,
            @RequestBody ThemeUpdateCommand command) {
        requireSuperAdmin(role);
        log.info("Workspace theme for {} changed by {} (#{})", workspaceRole, adminName, adminId);
        audit(adminId, "SUPER_ADMIN", workspaceRole, command);
        return ResponseEntity.ok(uiConfigService.updateTheme(workspaceRole, command));
    }

    @DeleteMapping("/workspaces/{workspaceRole}/theme")
    @Operation(summary = "Drop one workspace's theme override so it inherits the platform theme")
    public ResponseEntity<ResolvedTheme> resetWorkspaceTheme(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String workspaceRole) {
        requireSuperAdmin(role);
        uiConfigService.resetTheme(workspaceRole);
        return ResponseEntity.ok(uiConfigService.theme(workspaceRole));
    }

    // ------------------------------------------------------------------- one member's menu

    @GetMapping("/workspaces/{workspaceRole}/users/{userId}/menu")
    @Operation(summary = "One member's side menu, with their personal overrides marked")
    public ResponseEntity<List<UserMenuRow>> userMenu(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String workspaceRole,
            @PathVariable Long userId) {
        requireSuperAdmin(role);
        return ResponseEntity.ok(uiConfigService.userMenu(userId, workspaceRole));
    }

    @PutMapping("/workspaces/{workspaceRole}/users/{userId}/menu")
    @Operation(summary = "Hide or restore individual items for one member")
    public ResponseEntity<List<UserMenuRow>> updateUserMenu(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String workspaceRole,
            @PathVariable Long userId,
            @RequestBody List<UserMenuUpdateCommand> commands) {
        requireSuperAdmin(role);
        uiConfigService.updateUserMenu(userId, commands);
        return ResponseEntity.ok(uiConfigService.userMenu(userId, workspaceRole));
    }

    @DeleteMapping("/workspaces/{workspaceRole}/users/{userId}/menu")
    @Operation(summary = "Clear one member's personal menu overrides")
    public ResponseEntity<List<UserMenuRow>> resetUserMenu(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String workspaceRole,
            @PathVariable Long userId) {
        requireSuperAdmin(role);
        uiConfigService.resetUserMenu(userId);
        return ResponseEntity.ok(uiConfigService.userMenu(userId, workspaceRole));
    }

    private static void requireSuperAdmin(String role) {
        if (!"SUPER_ADMIN".equals(role)) {
            throw new AccessDeniedException("SUPER_ADMIN role required to change the platform's UI");
        }
    }

    /**
     * Before-state isn't captured here — the theme row this overwrites is a full read away and
     * theme edits aren't the kind of change that needs a diff to investigate, unlike a booking or
     * payment. The command itself, plus who and which scope, is what audit review actually needs.
     */
    private void audit(Long actorId, String actorRole, String scopeKey, ThemeUpdateCommand command) {
        auditPublisher.publish(AuditEventMessage.builder()
                .sourceService(SOURCE)
                .actorId(actorId)
                .actorRole(actorRole)
                .action(AuditAction.UPDATE)
                .entityType(ENTITY)
                .entityId(scopeKey)
                .afterState(command.toString())
                .build());
    }
}
