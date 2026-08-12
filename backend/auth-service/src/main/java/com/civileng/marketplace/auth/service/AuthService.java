package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.auth.dto.*;
import com.civileng.marketplace.auth.entity.Role;
import com.civileng.marketplace.auth.entity.User;
import com.civileng.marketplace.auth.entity.UserStatus;
import com.civileng.marketplace.auth.repository.RoleRepository;
import com.civileng.marketplace.auth.repository.UserRepository;
import com.civileng.marketplace.auth.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final OtpService otpService;
    private final RefreshTokenService refreshTokenService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 30;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailAndIsDeletedFalse(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        if (request.getPhone() != null &&
                userRepository.existsByPhoneAndIsDeletedFalse(request.getPhone())) {
            throw new IllegalArgumentException("Phone number already registered");
        }

        String roleName = request.getRole() != null ?
                request.getRole().toUpperCase() : "CUSTOMER";
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid role: " + roleName));

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(role)
                .status(UserStatus.PENDING_VERIFICATION)
                .build();

        user = userRepository.save(user);

        log.info("User registered successfully: {}", user.getEmail());

        kafkaTemplate.send("user.registered",
                Map.of("userId", user.getId(), "email", user.getEmail(),
                        "name", user.getName()));

        return buildAuthResponse(user, "Registration successful. Please verify your email.");
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndIsDeletedFalse(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (user.getStatus() == UserStatus.SUSPENDED ||
                user.getStatus() == UserStatus.BANNED) {
            throw new LockedException("Account is suspended or banned");
        }

        if (user.getLockedUntil() != null &&
                user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new LockedException("Account is locked. Try again later.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedLogin(user);
            throw new IllegalArgumentException("Invalid email or password");
        }

        userRepository.resetLoginAttempts(user.getId());
        userRepository.updateLastLogin(user.getId(), LocalDateTime.now());

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);
        }

        log.info("User logged in successfully: {}", user.getEmail());

        return buildAuthResponse(user, "Login successful");
    }

    public AuthResponse sendOtp(OtpRequest request) {
        if (!userRepository.existsByEmailAndIsDeletedFalse(request.getEmail())) {
            throw new IllegalArgumentException("Email not registered");
        }

        String otp = otpService.generateAndStoreOtp(request.getEmail());
        log.info("OTP sent to {}: {}", request.getEmail(), otp);

        kafkaTemplate.send("otp.sent",
                Map.of("email", request.getEmail(), "otp", otp));

        return AuthResponse.builder()
                .success(true)
                .message("OTP sent successfully")
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Transactional
    public AuthResponse verifyOtpAndLogin(OtpVerifyRequest request) {
        if (!otpService.validateOtp(request.getEmail(), request.getOtp())) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }

        User user = userRepository.findByEmailAndIsDeletedFalse(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setPhoneVerified(true);
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setEmailVerified(true);
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);
        userRepository.updateLastLogin(user.getId(), LocalDateTime.now());

        log.info("User logged in via OTP: {}", user.getEmail());

        return buildAuthResponse(user, "OTP verification successful");
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        try {
            var claims = jwtTokenProvider.validateToken(request.getRefreshToken());

            if (!"refresh".equals(claims.get("type"))) {
                throw new IllegalArgumentException("Invalid refresh token");
            }

            Long userId = Long.parseLong(claims.getSubject());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            return buildAuthResponse(user, "Token refreshed successfully");

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }
    }

    @Transactional
    public void logout(String accessToken) {
        jwtTokenProvider.blacklistToken(accessToken);
        log.info("User logged out, token blacklisted");
    }

    private void handleFailedLogin(User user) {
        userRepository.incrementLoginAttempts(user.getId());

        if (user.getLoginAttempts() + 1 >= MAX_LOGIN_ATTEMPTS) {
            userRepository.lockAccount(user.getId(),
                    LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
            log.warn("Account locked due to multiple failed attempts: {}", user.getEmail());
        }
    }

    private AuthResponse buildAuthResponse(User user, String message) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId().toString(),
                user.getEmail(),
                user.getRole().getName(),
                user.getName()
        );

        String refreshToken = jwtTokenProvider.generateRefreshToken(
                user.getId().toString());
        refreshTokenService.storeRefreshToken(user.getId().toString(), refreshToken);

        AuthResponse.UserDto userDto = AuthResponse.UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .profilePicture(user.getProfilePicture())
                .role(user.getRole().getName())
                .emailVerified(user.getEmailVerified())
                .phoneVerified(user.getPhoneVerified())
                .status(user.getStatus().name())
                .build();

        return AuthResponse.builder()
                .success(true)
                .message(message)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .user(userDto)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
