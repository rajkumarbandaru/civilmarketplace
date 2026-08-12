package com.civileng.marketplace.auth.controller;

import com.civileng.marketplace.auth.entity.Role;
import com.civileng.marketplace.auth.entity.User;
import com.civileng.marketplace.auth.entity.UserStatus;
import com.civileng.marketplace.auth.repository.RoleRepository;
import com.civileng.marketplace.auth.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin User Management (Auth)", description = "Admin endpoints for user CRUD and management")
public class AdminUserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @GetMapping("/users")
    @Operation(summary = "Get paginated list of all users with search and filters")
    public ResponseEntity<Map<String, Object>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Convert string params to typed values for the DB-level query
        String searchParam = (search != null && !search.isBlank()) ? search.trim() : null;
        String roleParam = (role != null && !role.isBlank()) ? role.toUpperCase() : null;
        UserStatus statusParam = null;
        if (status != null && !status.isBlank()) {
            try {
                statusParam = UserStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                // Invalid status filter, treat as no filter
            }
        }

        // Use single DB-level query with all filters — pagination metadata is now correct
        Page<User> userPage = userRepository.findAdminUsers(
                searchParam, roleParam, statusParam, pageable);

        var users = userPage.getContent().stream()
                .map(this::toUserMap)
                .toList();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", users,
                "page", userPage.getNumber(),
                "size", userPage.getSize(),
                "totalElements", userPage.getTotalElements(),
                "totalPages", userPage.getTotalPages()
        ));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get user by ID with full details")
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        return ResponseEntity.ok(Map.of("success", true, "data", toUserMap(user)));
    }

    @GetMapping("/users/{userId}/name")
    @Operation(summary = "Get user's display name by ID — lightweight endpoint for cross-service resolution")
    public ResponseEntity<Map<String, Object>> getUserName(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElse(null);
        if (user == null || user.getIsDeleted()) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "userId", userId,
                    "name", "User #" + userId,
                    "exists", false
            ));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "userId", userId,
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole().getName(),
                "exists", true
        ));
    }

    @PutMapping("/users/{userId}")
    @Operation(summary = "Update user details")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        if (request.getName() != null) user.setName(request.getName());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getRole() != null) {
            Role role = roleRepository.findByName(request.getRole().toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid role: " + request.getRole()));
            user.setRole(role);
        }

        userRepository.save(user);
        log.info("Admin updated user: {}", userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "User updated successfully", "data", toUserMap(user)));
    }

    @PutMapping("/users/{userId}/status")
    @Operation(summary = "Update user status (activate, suspend, ban)")
    public ResponseEntity<Map<String, Object>> updateUserStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateStatusRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        try {
            UserStatus newStatus = UserStatus.valueOf(request.getStatus().toUpperCase());
            user.setStatus(newStatus);
            if (newStatus == UserStatus.SUSPENDED || newStatus == UserStatus.BANNED) {
                user.setLockedUntil(LocalDateTime.now().plusYears(10));
            } else {
                user.setLockedUntil(null);
            }
            userRepository.save(user);
            log.info("Admin updated user {} status to {}", userId, newStatus);
            return ResponseEntity.ok(Map.of("success", true, "message", "User status updated to " + newStatus));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + request.getStatus() +
                    ". Valid values: ACTIVE, INACTIVE, SUSPENDED, BANNED, PENDING_VERIFICATION");
        }
    }

    @DeleteMapping("/users/{userId}")
    @Operation(summary = "Soft-delete a user")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        user.setIsDeleted(true);
        user.setStatus(UserStatus.DELETED);
        userRepository.save(user);
        log.info("Admin soft-deleted user: {}", userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "User deleted successfully"));
    }

    /**
     * The role catalogue with live member counts. Roles live only in this service, so anything
     * that needs "every role on the platform" — the UI-config workspace list in admin-service —
     * has to read it from here rather than keeping a copy that silently drifts.
     */
    @GetMapping("/roles")
    @Operation(summary = "List every role with its live user count")
    public ResponseEntity<Map<String, Object>> getRoles() {
        Map<String, Long> counts = new java.util.HashMap<>();
        for (Object[] row : userRepository.countUsersByRoleName()) {
            counts.put((String) row[0], (Long) row[1]);
        }

        var roles = roleRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Role::getName))
                .map(role -> Map.of(
                        "name", role.getName(),
                        "description", role.getDescription() != null ? role.getDescription() : "",
                        "systemRole", Boolean.TRUE.equals(role.getIsSystemRole()),
                        "userCount", counts.getOrDefault(role.getName(), 0L)
                ))
                .toList();

        return ResponseEntity.ok(Map.of("success", true, "data", roles));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get user statistics summary")
    public ResponseEntity<Map<String, Object>> getUserStats() {
        long totalUsers = userRepository.count();
        long pendingVerifications = userRepository.countByStatus(UserStatus.PENDING_VERIFICATION);
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);
        long suspendedUsers = userRepository.countByStatus(UserStatus.SUSPENDED);
        long bannedUsers = userRepository.countByStatus(UserStatus.BANNED);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "totalUsers", totalUsers,
                "activeUsers", activeUsers,
                "pendingVerifications", pendingVerifications,
                "suspendedUsers", suspendedUsers,
                "bannedUsers", bannedUsers
        ));
    }

    private Map<String, Object> toUserMap(User user) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("name", user.getName());
        map.put("email", user.getEmail());
        map.put("phone", user.getPhone() != null ? user.getPhone() : "");
        map.put("role", user.getRole().getName());
        map.put("status", user.getStatus().name());
        map.put("profilePicture", user.getProfilePicture() != null ? user.getProfilePicture() : "");
        map.put("emailVerified", user.getEmailVerified());
        map.put("phoneVerified", user.getPhoneVerified());
        map.put("provider", user.getProvider() != null ? user.getProvider() : "");
        map.put("twoFactorEnabled", user.getTwoFactorEnabled());
        map.put("lastLoginAt", user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null);
        map.put("joinedAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        map.put("bookings", 0);
        map.put("rating", 0.0);
        map.put("city", "");
        return map;
    }

    @Data
    public static class UpdateUserRequest {
        private String name;
        private String email;
        private String phone;
        private String role;
    }

    @Data
    public static class UpdateStatusRequest {
        @NotBlank(message = "Status is required")
        private String status;
        private String reason;
    }
}
