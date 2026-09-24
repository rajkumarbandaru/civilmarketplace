package com.civileng.marketplace.auth.controller;

import com.civileng.marketplace.auth.dto.AuthResponse;
import com.civileng.marketplace.auth.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/me")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "The signed-in user's own profile")
public class ProfileController {

    private final ProfileService profileService;

    public record ProfilePictureRequest(@NotBlank String mediaId) { }

    @PutMapping("/profile-picture")
    @Operation(summary = "Use an uploaded AVATAR file as my profile picture")
    public ResponseEntity<AuthResponse.UserDto> setProfilePicture(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ProfilePictureRequest request) {
        return ResponseEntity.ok(profileService.setProfilePicture(authorization, request.mediaId()));
    }

    @DeleteMapping("/profile-picture")
    @Operation(summary = "Remove my profile picture")
    public ResponseEntity<AuthResponse.UserDto> clearProfilePicture(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResponseEntity.ok(profileService.clearProfilePicture(authorization));
    }
}
