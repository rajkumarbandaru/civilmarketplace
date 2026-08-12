package com.civileng.marketplace.admin.controller;

import com.civileng.marketplace.admin.dto.UserDTO;
import com.civileng.marketplace.admin.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin User Management", description = "Admin user CRUD and management APIs")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "Get paginated list of all users")
    public ResponseEntity<Map<String, Object>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        Map<String, Object> result = adminUserService.getUsers(page, size, search, role, status);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID with full details")
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable Long userId) {
        Map<String, Object> result = adminUserService.getUserById(userId);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Update user details (name, email, role, etc.)")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UserDTO.UpdateUserRequest request) {
        Map<String, Object> result = adminUserService.updateUser(userId, request);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{userId}/status")
    @Operation(summary = "Update user status (activate, suspend, ban)")
    public ResponseEntity<Map<String, Object>> updateUserStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UserDTO.UpdateStatusRequest request) {
        Map<String, Object> result = adminUserService.updateUserStatus(userId, request);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Soft-delete a user account")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long userId) {
        Map<String, Object> result = adminUserService.deleteUser(userId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get user statistics summary")
    public ResponseEntity<Map<String, Object>> getUserStats() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "totalUsers", 12847,
                "totalEngineers", 2847,
                "pendingVerifications", 143,
                "activeUsers", 12400
        ));
    }
}
