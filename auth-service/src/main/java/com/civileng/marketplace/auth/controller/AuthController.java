package com.civileng.marketplace.auth.controller;

import com.civileng.marketplace.auth.dto.*;
import com.civileng.marketplace.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and Authorization APIs")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input or email already exists")
    })
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/otp/send")
    @Operation(summary = "Send OTP to email/phone")
    public ResponseEntity<AuthResponse> sendOtp(
            @Valid @RequestBody OtpRequest request) {
        AuthResponse response = authService.sendOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify OTP and login")
    public ResponseEntity<AuthResponse> verifyOtp(
            @Valid @RequestBody OtpVerifyRequest request) {
        AuthResponse response = authService.verifyOtpAndLogin(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<AuthResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and invalidate token")
    public ResponseEntity<Map<String, Object>> logout(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        authService.logout(token);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Logged out successfully"
        ));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check endpoint")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "service", "auth-service",
                "status", "UP",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
