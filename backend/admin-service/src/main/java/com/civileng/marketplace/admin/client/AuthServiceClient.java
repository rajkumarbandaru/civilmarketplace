package com.civileng.marketplace.admin.client;

import com.civileng.marketplace.admin.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "auth-service", path = "/api/v1/auth")
public interface AuthServiceClient {

    @GetMapping("/admin/users")
    ResponseEntity<Map<String, Object>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status);

    @GetMapping("/admin/users/{userId}")
    ResponseEntity<Map<String, Object>> getUserById(@PathVariable Long userId);

    @PutMapping("/admin/users/{userId}")
    ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable Long userId,
            @RequestBody UserDTO.UpdateUserRequest request);

    @PutMapping("/admin/users/{userId}/status")
    ResponseEntity<Map<String, Object>> updateUserStatus(
            @PathVariable Long userId,
            @RequestBody UserDTO.UpdateStatusRequest request);

    @DeleteMapping("/admin/users/{userId}")
    ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long userId);

    @GetMapping("/admin/stats")
    ResponseEntity<Map<String, Object>> getUserStats();

    /** Every role on the platform with its live user count — the workspace list for UI-config. */
    @GetMapping("/admin/roles")
    ResponseEntity<Map<String, Object>> getRoles();

    /**
     * Creates a role, i.e. a workspace. The actor's role travels as an explicit header because
     * Feign does not forward the inbound request's headers, and auth-service gates this endpoint
     * on SUPER_ADMIN in its own right.
     */
    @PostMapping("/admin/roles")
    ResponseEntity<Map<String, Object>> createRole(
            @RequestHeader("X-User-Role") String actorRole,
            @RequestBody Map<String, String> request);
}
