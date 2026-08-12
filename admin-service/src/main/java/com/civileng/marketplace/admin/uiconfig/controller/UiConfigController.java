package com.civileng.marketplace.admin.uiconfig.controller;

import com.civileng.marketplace.admin.exception.AccessDeniedException;
import com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.AppearanceSettings;
import com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.AppearanceUpdateCommand;
import com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.Snapshot;
import com.civileng.marketplace.admin.uiconfig.service.UiConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The client-facing read of the dynamic UI config: one call at sign-in returns the side menu and
 * theme for the caller's workspace, plus the two appearance settings the caller owns.
 *
 * <p>Identity comes from the gateway's JWT filter as {@code X-User-*} headers — this service
 * never verifies the token itself. The headers are declared optional and null-checked explicitly:
 * a missing <em>required</em> header throws before the handler runs and falls through to the
 * generic 500 handler instead of a proper 4xx.
 */
@RestController
@RequestMapping("/api/v1/ui-config")
@RequiredArgsConstructor
@Tag(name = "UI Configuration", description = "Dynamic side menu, theme and appearance for the signed-in user")
public class UiConfigController {

    private final UiConfigService uiConfigService;

    @GetMapping("/me")
    @Operation(summary = "The signed-in user's side menu and theme")
    public ResponseEntity<Snapshot> mySnapshot(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestParam(value = "role", required = false) String requestedRole) {

        requireUser(userId, userRole);
        return ResponseEntity.ok(uiConfigService.snapshotFor(userId, resolveRole(userRole, requestedRole)));
    }

    /**
     * What the caller's workspace looks like, what they chose themselves, and which parts only
     * Super Admin can change. Separate from {@link #mySnapshot} because the shell fetches the
     * snapshot on every load, while this is read only when someone opens their settings.
     */
    @GetMapping("/me/appearance")
    @Operation(summary = "The signed-in user's appearance settings")
    public ResponseEntity<AppearanceSettings> myAppearance(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestParam(value = "role", required = false) String requestedRole) {

        requireUser(userId, userRole);
        return ResponseEntity.ok(uiConfigService.myAppearance(userId, resolveRole(userRole, requestedRole)));
    }

    /**
     * The member changing their own two settings. Scoped to the caller from the gateway headers —
     * there is no user id in the path, so this endpoint cannot be aimed at somebody else.
     */
    @PutMapping("/me/appearance")
    @Operation(summary = "Change the signed-in user's colour mode and density")
    public ResponseEntity<AppearanceSettings> updateMyAppearance(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestParam(value = "role", required = false) String requestedRole,
            @RequestBody AppearanceUpdateCommand command) {

        requireUser(userId, userRole);
        return ResponseEntity.ok(
                uiConfigService.updateMyAppearance(userId, resolveRole(userRole, requestedRole), command));
    }

    @DeleteMapping("/me/appearance")
    @Operation(summary = "Follow the workspace again on both appearance settings")
    public ResponseEntity<AppearanceSettings> resetMyAppearance(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestParam(value = "role", required = false) String requestedRole) {

        requireUser(userId, userRole);
        return ResponseEntity.ok(
                uiConfigService.resetMyAppearance(userId, resolveRole(userRole, requestedRole)));
    }

    private static void requireUser(Long userId, String userRole) {
        if (userId == null || userRole == null || userRole.isBlank()) {
            throw new AccessDeniedException("Authentication required");
        }
    }

    /**
     * Which workspace the caller is asking about. A user holds exactly one role here, so the
     * header is the answer — except for Super Admin, who may name any role, which is how the
     * console previews another workspace's shell. Anyone else naming a role they do not hold is
     * refused rather than quietly served their own, or the console would show a preview that
     * silently is not one.
     */
    private static String resolveRole(String userRole, String requestedRole) {
        if (requestedRole == null || requestedRole.isBlank() || requestedRole.equals(userRole)) {
            return userRole;
        }
        if (!"SUPER_ADMIN".equals(userRole)) {
            throw new AccessDeniedException("You do not hold the role " + requestedRole);
        }
        return requestedRole;
    }
}
