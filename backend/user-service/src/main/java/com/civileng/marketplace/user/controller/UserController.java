package com.civileng.marketplace.user.controller;

import com.civileng.marketplace.user.model.Address;
import com.civileng.marketplace.user.model.UserProfile;
import com.civileng.marketplace.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "User profile and address management APIs")
public class UserController {

    private final UserService userService;

    @PostMapping("/profile")
    @Operation(summary = "Create user profile")
    public ResponseEntity<UserProfile> createProfile(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody UserProfile profile) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.createProfile(userId, profile));
    }

    @GetMapping("/profile")
    @Operation(summary = "Get user profile")
    public ResponseEntity<UserProfile> getProfile(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update user profile")
    public ResponseEntity<UserProfile> updateProfile(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody UserProfile profile) {
        return ResponseEntity.ok(userService.updateProfile(userId, profile));
    }

    @PostMapping("/addresses")
    @Operation(summary = "Add new address")
    public ResponseEntity<Address> addAddress(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody Address address) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.addAddress(userId, address));
    }

    @GetMapping("/addresses")
    @Operation(summary = "Get all user addresses")
    public ResponseEntity<List<Address>> getAddresses(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(userService.getUserAddresses(userId));
    }

    @PutMapping("/addresses/{addressId}")
    @Operation(summary = "Update address")
    public ResponseEntity<Address> updateAddress(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long addressId,
            @RequestBody Address address) {
        return ResponseEntity.ok(
                userService.updateAddress(userId, addressId, address));
    }

    @DeleteMapping("/addresses/{addressId}")
    @Operation(summary = "Delete address")
    public ResponseEntity<Map<String, Object>> deleteAddress(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long addressId) {
        userService.deleteAddress(userId, addressId);
        return ResponseEntity.ok(Map.of("success", true,
                "message", "Address deleted successfully"));
    }

    @PutMapping("/addresses/{addressId}/default")
    @Operation(summary = "Set address as default")
    public ResponseEntity<Map<String, Object>> setDefaultAddress(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long addressId) {
        userService.setDefaultAddress(userId, addressId);
        return ResponseEntity.ok(Map.of("success", true,
                "message", "Default address updated"));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "service", "user-service",
                "status", "UP",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
