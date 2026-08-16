package com.civileng.marketplace.notification.controller;

import com.civileng.marketplace.notification.dto.EmailLogDto.LogResponse;
import com.civileng.marketplace.notification.dto.EmailLogDto.LogSummary;
import com.civileng.marketplace.notification.exception.AccessDeniedException;
import com.civileng.marketplace.notification.model.EmailStatus;
import com.civileng.marketplace.notification.model.NotificationChannel;
import com.civileng.marketplace.notification.service.EmailLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * The Notifications screen: every message the platform tried to send on any channel, and how far
 * it got.
 *
 * <p>Read-only by design. Nothing here edits a delivery — the record of what a customer was sent
 * is evidence, and support staff need to be able to read it without being able to rewrite it.
 */
@RestController
@RequestMapping("/api/v1/admin/notifications/emails")
@RequiredArgsConstructor
@Tag(name = "Admin Notification Log",
        description = "Delivery status of every notification, on every channel")
public class AdminEmailLogController {

    private static final Set<String> ADMIN_ROLES =
            Set.of("SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN");

    private final EmailLogService emailLogService;

    @GetMapping
    @Operation(summary = "Sent notifications, newest first, filterable by status, channel, source and recipient")
    public ResponseEntity<Page<LogResponse>> list(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String templateKey,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        requireAdmin(role);
        return ResponseEntity.ok(emailLogService.search(
                parseStatus(status), parseChannel(channel), templateKey, search,
                PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping("/summary")
    @Operation(summary = "Counts per status and per channel, for the tiles above the list")
    public ResponseEntity<LogSummary> summary(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        return ResponseEntity.ok(emailLogService.summary());
    }

    @GetMapping("/template-keys")
    @Operation(summary = "Sources present in the log (template keys and notification types)")
    public ResponseEntity<List<String>> templateKeys(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        return ResponseEntity.ok(emailLogService.loggedTemplateKeys());
    }

    @GetMapping("/{id}")
    @Operation(summary = "One send attempt in full, including the provider's error")
    public ResponseEntity<LogResponse> get(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long id) {
        requireAdmin(role);
        LogResponse found = emailLogService.get(id);
        if (found == null) {
            throw new NoSuchElementException("No email log entry with id " + id);
        }
        return ResponseEntity.ok(found);
    }

    /** An unknown channel name is a typo in the query string, not an empty result. */
    private static NotificationChannel parseChannel(String channel) {
        if (channel == null || channel.isBlank() || "ALL".equalsIgnoreCase(channel)) {
            return null;
        }
        try {
            return NotificationChannel.valueOf(channel.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown notification channel '" + channel + "'");
        }
    }

    /** An unknown status name is a typo in the query string, not an empty result. */
    private static EmailStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return EmailStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown email status '" + status + "'");
        }
    }

    private void requireAdmin(String role) {
        if (role == null || !ADMIN_ROLES.contains(role)) {
            throw new AccessDeniedException("Admin role required");
        }
    }
}
