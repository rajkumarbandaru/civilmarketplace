package com.civileng.marketplace.admin.settings.controller;

import com.civileng.marketplace.admin.exception.AccessDeniedException;
import com.civileng.marketplace.admin.settings.service.PlatformSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Platform settings — the switches that change how the platform behaves for everybody.
 *
 * <p>Gated on SUPER_ADMIN, like the UI configuration and for the same reason: fee percentages,
 * registration and maintenance mode are platform-wide decisions, not per-admin ones. Identity
 * arrives as {@code X-User-*} headers from the gateway; this service never reads the token.
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Platform Settings", description = "Super Admin control of platform-wide settings")
public class PlatformSettingsController {

    private final PlatformSettingsService settingsService;

    @GetMapping
    @Operation(summary = "Every platform setting, grouped, with its current value")
    public ResponseEntity<Map<String, Object>> settings(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireSuperAdmin(role);
        return ResponseEntity.ok(Map.of("success", true, "data", settingsService.settings()));
    }

    /**
     * The effective values alone. Separate from the screen's shape so a service reading a setting
     * does not have to walk a structure built for rendering.
     */
    @GetMapping("/values")
    @Operation(summary = "The effective value of every setting, keyed by setting")
    public ResponseEntity<Map<String, Object>> values(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireSuperAdmin(role);
        return ResponseEntity.ok(Map.of("success", true, "data", settingsService.effectiveValues()));
    }

    @PutMapping
    @Operation(summary = "Save changed settings")
    public ResponseEntity<Map<String, Object>> update(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @RequestBody Map<String, String> changes) {
        requireSuperAdmin(role);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Settings saved",
                "data", settingsService.update(changes, adminId)));
    }

    @DeleteMapping("/{key}")
    @Operation(summary = "Return one setting to its shipped default")
    public ResponseEntity<Map<String, Object>> reset(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String key) {
        requireSuperAdmin(role);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Setting reset to its default",
                "data", settingsService.reset(key)));
    }

    private static void requireSuperAdmin(String role) {
        if (!"SUPER_ADMIN".equals(role)) {
            throw new AccessDeniedException("SUPER_ADMIN role required to change platform settings");
        }
    }
}
