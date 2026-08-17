package com.civileng.marketplace.notification.controller;

import com.civileng.marketplace.notification.dto.AnnouncementDto.CreateAnnouncementRequest;
import com.civileng.marketplace.notification.exception.AccessDeniedException;
import com.civileng.marketplace.notification.model.Announcement;
import com.civileng.marketplace.notification.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * Super Admin / staff broadcast console. Publishing with no {@code scheduledAt} fans out
 * immediately, matching the SRS's "one-click" framing for ENT·04; with one, the announcement
 * waits for its time and {@code AnnouncementReleaseJob} sends it. There is still no draft state —
 * a scheduled announcement is committed, only not yet delivered, and cancelling is how it is
 * called off.
 */
@RestController
@RequestMapping("/api/v1/admin/announcements")
@RequiredArgsConstructor
@Tag(name = "Admin Announcements", description = "Platform-wide broadcast to a role or everyone")
public class AdminAnnouncementController {

    private static final Set<String> ADMIN_ROLES =
            Set.of("SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN");

    private final AnnouncementService announcementService;

    @PostMapping
    @Operation(summary = "Publish an announcement to a role, several roles, or everyone")
    public ResponseEntity<Announcement> publish(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody CreateAnnouncementRequest request) {
        requireAdmin(role);
        return ResponseEntity.ok(announcementService.publish(actorId, role, request));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Call off an announcement that has not gone out yet")
    public ResponseEntity<Announcement> cancel(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long id) {
        requireAdmin(role);
        return ResponseEntity.ok(announcementService.cancel(actorId, role, id));
    }

    @GetMapping
    @Operation(summary = "Announcement history, most recent first")
    public ResponseEntity<Page<Announcement>> history(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        return ResponseEntity.ok(announcementService.listHistory(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    /**
     * The role header is optional so a missing one produces a 403 here rather than a generic 500
     * from the framework before this method ever runs.
     */
    private void requireAdmin(String role) {
        if (role == null || !ADMIN_ROLES.contains(role)) {
            throw new AccessDeniedException("Admin role required");
        }
    }
}
