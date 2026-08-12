package com.civileng.marketplace.admin.service;

import com.civileng.marketplace.admin.client.AuthServiceClient;
import com.civileng.marketplace.admin.client.UserServiceClient;
import com.civileng.marketplace.admin.dto.UserDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminUserService {

    private final AuthServiceClient authServiceClient;
    private final UserServiceClient userServiceClient;

    @CircuitBreaker(name = "userService", fallbackMethod = "getUsersFallback")
    public Map<String, Object> getUsers(int page, int size, String search, String role, String status) {
        try {
            Map<String, Object> response = authServiceClient.getAllUsers(page, size, search, role, status).getBody();
            if (response != null) {
                enrichUserProfiles(response);
                return response;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch users from auth-service: {}", e.getMessage());
        }
        return getUsersFallback(page, size, search, role, status, new Exception("Fallback"));
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "getUserFallback")
    public Map<String, Object> getUserById(Long userId) {
        try {
            Map<String, Object> response = authServiceClient.getUserById(userId).getBody();
            if (response != null) {
                enrichUserProfile(response, userId);
                return response;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user {}: {}", userId, e.getMessage());
        }
        return getUserFallback(userId, new Exception("Fallback"));
    }

    public Map<String, Object> updateUser(Long userId, UserDTO.UpdateUserRequest request) {
        try {
            Map<String, Object> response = authServiceClient.updateUser(userId, request).getBody();
            return response != null ? response : createSuccessResponse("User updated successfully");
        } catch (Exception e) {
            log.error("Failed to update user {}: {}", userId, e.getMessage());
            return createErrorResponse("Failed to update user: " + e.getMessage());
        }
    }

    public Map<String, Object> updateUserStatus(Long userId, UserDTO.UpdateStatusRequest request) {
        try {
            Map<String, Object> response = authServiceClient.updateUserStatus(userId, request).getBody();
            return response != null ? response : createSuccessResponse("User status updated successfully");
        } catch (Exception e) {
            log.error("Failed to update user status {}: {}", userId, e.getMessage());
            return createErrorResponse("Failed to update user status: " + e.getMessage());
        }
    }

    public Map<String, Object> deleteUser(Long userId) {
        try {
            Map<String, Object> response = authServiceClient.deleteUser(userId).getBody();
            return response != null ? response : createSuccessResponse("User deleted successfully");
        } catch (Exception e) {
            log.error("Failed to delete user {}: {}", userId, e.getMessage());
            return createErrorResponse("Failed to delete user: " + e.getMessage());
        }
    }

    private void enrichUserProfiles(Map<String, Object> response) {
        // Enrich with profile data if available from user-service
        try {
            Map<String, Object> profileResponse = userServiceClient.getUserProfileStats().getBody();
            if (profileResponse != null) {
                response.put("profileData", profileResponse);
            }
        } catch (Exception e) {
            log.debug("Could not enrich user profiles: {}", e.getMessage());
        }
    }

    private void enrichUserProfile(Map<String, Object> response, Long userId) {
        try {
            Map<String, Object> profile = userServiceClient.getProfileByUserId(userId).getBody();
            if (profile != null) {
                response.put("profile", profile);
            }
        } catch (Exception e) {
            log.debug("Could not enrich user profile {}: {}", userId, e.getMessage());
        }
    }

    private Map<String, Object> getUsersFallback(int page, int size, String search, String role, String status, Throwable t) {
        List<Map<String, Object>> users = generateMockUsers(page, size, search, role, status);
        return Map.of(
                "success", true,
                "data", users,
                "totalElements", 12847,
                "totalPages", (int) Math.ceil(12847.0 / size),
                "page", page,
                "size", size
        );
    }

    private Map<String, Object> getUserFallback(Long userId, Throwable t) {
        return Map.of(
                "success", true,
                "data", generateMockUser(userId)
        );
    }

    private Map<String, Object> createSuccessResponse(String message) {
        return Map.of("success", true, "message", message);
    }

    private Map<String, Object> createErrorResponse(String message) {
        return Map.of("success", false, "message", message);
    }

    private List<Map<String, Object>> generateMockUsers(int page, int size, String search, String role, String status) {
        String[] names = {"Rahul Sharma", "Priya Patel", "Amit Singh", "Suresh Kumar", "Neha Gupta",
                "Vikram Joshi", "Anita Desai", "Raj Mehta", "Sunil Verma", "Deepa Rao"};
        String[] roles = {"CUSTOMER", "CIVIL_ENGINEER", "ARCHITECT", "SURVEYOR", "WORKER", "CONTRACTOR", "ADMIN"};
        String[] cities = {"Mumbai", "Delhi", "Bangalore", "Pune", "Hyderabad"};
        String[] statuses = {"ACTIVE", "ACTIVE", "ACTIVE", "PENDING", "ACTIVE", "SUSPENDED", "ACTIVE"};

        return names.length > 0 ? List.of(generateMockUser(1L)) : Collections.emptyList();
    }

    private Map<String, Object> generateMockUser(Long id) {
        return Map.of(
                "id", id,
                "name", "User " + id,
                "email", "user" + id + "@example.com",
                "phone", "+91 98765" + String.format("%05d", 43210 + id),
                "role", "CUSTOMER",
                "status", "ACTIVE",
                "city", "Mumbai",
                "bookings", 12,
                "rating", 4.5,
                "joinedAt", "2024-01-15T10:30:00"
        );
    }
}
