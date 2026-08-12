package com.civileng.marketplace.notification.controller;

import com.civileng.marketplace.notification.model.Notification;
import com.civileng.marketplace.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User notification management APIs")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Get user notifications")
    public ResponseEntity<Page<Notification>> getNotifications(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.getUserNotifications(
                userId, PageRequest.of(page, size,
                        Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(
            @RequestHeader("X-User-Id") Long userId) {
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PutMapping("/{notificationId}/read")
    @Operation(summary = "Mark notification as read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long notificationId,
            @RequestHeader("X-User-Id") Long userId) {
        notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<Map<String, Object>> markAllAsRead(
            @RequestHeader("X-User-Id") Long userId) {
        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("markedAsRead", count));
    }

    @DeleteMapping("/{notificationId}")
    @Operation(summary = "Delete notification")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable Long notificationId,
            @RequestHeader("X-User-Id") Long userId) {
        notificationService.deleteNotification(notificationId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "service", "notification-service",
                "status", "UP",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
