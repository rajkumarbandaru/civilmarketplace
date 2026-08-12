package com.civileng.marketplace.user.controller;

import com.civileng.marketplace.user.model.KycDocument;
import com.civileng.marketplace.user.model.UserProfile;
import com.civileng.marketplace.user.repository.KycDocumentRepository;
import com.civileng.marketplace.user.repository.UserProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Profile Management (User)", description = "Admin endpoints for user profile data")
public class AdminProfileController {

    private final UserProfileRepository userProfileRepository;
    private final KycDocumentRepository kycDocumentRepository;

    @GetMapping("/profiles")
    @Operation(summary = "Get all user profiles with pagination")
    public ResponseEntity<Map<String, Object>> getAllProfiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserProfile> profilePage = userProfileRepository.findAll(pageable);

        var profiles = profilePage.getContent().stream()
                .map(this::toProfileMap)
                .toList();

        return ResponseEntity.ok(Map.of(
                "success", true, "data", profiles,
                "page", page, "size", size,
                "totalElements", profilePage.getTotalElements(),
                "totalPages", profilePage.getTotalPages()
        ));
    }

    @GetMapping("/profiles/{userId}")
    @Operation(summary = "Get profile by user ID")
    public ResponseEntity<Map<String, Object>> getProfileByUserId(@PathVariable Long userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElse(null);
        if (profile == null) {
            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("success", true);
            response.put("data", null);
            response.put("message", "No profile found for user " + userId);
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.ok(Map.of("success", true, "data", toProfileMap(profile)));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get user profile statistics")
    public ResponseEntity<Map<String, Object>> getUserProfileStats() {
        long totalProfiles = userProfileRepository.count();
        long verifiedProfiles = userProfileRepository.countByIsVerifiedTrue();
        long availableWorkers = userProfileRepository.countByIsAvailableTrue();
        long pendingKyc = kycDocumentRepository.countByStatus(KycDocument.KycStatus.PENDING);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "totalProfiles", totalProfiles,
                "verifiedProfiles", verifiedProfiles,
                "availableWorkers", availableWorkers,
                "pendingKyc", pendingKyc
        ));
    }

    private Map<String, Object> toProfileMap(UserProfile p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("userId", p.getUserId());
        map.put("city", p.getCity() != null ? p.getCity() : "");
        map.put("state", p.getState() != null ? p.getState() : "");
        map.put("bio", p.getBio() != null ? p.getBio() : "");
        map.put("languages", p.getLanguages() != null ? p.getLanguages() : "");
        map.put("experienceYears", p.getExperienceYears() != null ? p.getExperienceYears() : 0);
        map.put("hourlyRate", p.getHourlyRate() != null ? p.getHourlyRate() : 0.0);
        map.put("isVerified", p.getIsVerified());
        map.put("isAvailable", p.getIsAvailable());
        map.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        return map;
    }
}
