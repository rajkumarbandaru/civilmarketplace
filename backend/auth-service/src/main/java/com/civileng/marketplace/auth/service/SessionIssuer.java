package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.auth.dto.AuthResponse;
import com.civileng.marketplace.auth.entity.User;
import com.civileng.marketplace.auth.security.JwtTokenProvider;
import com.civileng.marketplace.tenant.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Mints a session — access token, a refresh token registered for rotation, and the user — for a
 * sign-in that has passed every factor it needs. The one place tokens are issued, so no path
 * (password, OTP, social, MFA, refresh) can forget to register its refresh token.
 */
@Component
@RequiredArgsConstructor
public class SessionIssuer {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public AuthResponse issue(User user, String message) {
        String tenant = TenantContext.require();
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId().toString(), user.getEmail(), user.getRole().getName(), user.getName(), tenant);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId().toString(), tenant);
        refreshTokenService.storeRefreshToken(user.getId().toString(), refreshToken);

        return AuthResponse.builder()
                .success(true)
                .message(message)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .user(toDto(user))
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static AuthResponse.UserDto toDto(User user) {
        return AuthResponse.UserDto.builder()
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
    }
}
