package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.auth.dto.AuthResponse;
import com.civileng.marketplace.auth.exception.UnauthenticatedException;
import com.civileng.marketplace.auth.entity.User;
import com.civileng.marketplace.auth.repository.UserRepository;
import com.civileng.marketplace.auth.security.JwtTokenProvider;
import com.civileng.marketplace.tenant.common.TenantContext;
import com.civileng.marketplace.web.common.client.MediaRef;
import com.civileng.marketplace.web.common.client.MediaReferences;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The signed-in user's own profile settings, starting with their photo.
 *
 * <p>Identity comes from the bearer token, verified here: the gateway lets {@code /api/v1/auth/**}
 * through without its JWT filter (login and register must work without a token), so no
 * {@code X-User-Id} header on this route can be trusted.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProfileService {

    static final String AVATAR = "AVATAR";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final MediaReferences mediaReferences;

    /** Sets the photo to an uploaded AVATAR file — only one the same user uploaded. */
    @Transactional
    public AuthResponse.UserDto setProfilePicture(String authorization, String mediaId) {
        User user = currentUser(authorization);
        MediaRef ref = mediaReferences.requireOwned(mediaId, AVATAR, user.getId());
        user.setProfilePicture(ref.url());
        log.info("User {} changed their profile picture to media {}", user.getId(), ref.id());
        return toDto(userRepository.save(user));
    }

    @Transactional
    public AuthResponse.UserDto clearProfilePicture(String authorization) {
        User user = currentUser(authorization);
        user.setProfilePicture(null);
        return toDto(userRepository.save(user));
    }

    private User currentUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthenticatedException("Sign in to change your profile");
        }
        Claims claims;
        try {
            claims = jwtTokenProvider.validateToken(authorization.substring(7));
        } catch (RuntimeException e) {
            throw new UnauthenticatedException("Your session has expired, sign in again");
        }
        // A refresh token is not a key to the API, and a token is only good on its own workspace.
        if ("refresh".equals(claims.get("type", String.class))
                || !TenantContext.require().equals(claims.get("tenant", String.class))) {
            throw new UnauthenticatedException("Your session has expired, sign in again");
        }
        return userRepository.findById(Long.parseLong(claims.getSubject()))
                .orElseThrow(() -> new SecurityException("Your session has expired, sign in again"));
    }

    private static AuthResponse.UserDto toDto(User user) {
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
