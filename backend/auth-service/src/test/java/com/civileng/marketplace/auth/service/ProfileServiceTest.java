package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.auth.dto.AuthResponse;
import com.civileng.marketplace.auth.entity.Role;
import com.civileng.marketplace.auth.entity.User;
import com.civileng.marketplace.auth.entity.UserStatus;
import com.civileng.marketplace.auth.exception.UnauthenticatedException;
import com.civileng.marketplace.auth.repository.UserRepository;
import com.civileng.marketplace.auth.security.JwtTokenProvider;
import com.civileng.marketplace.tenant.common.TenantContext;
import com.civileng.marketplace.web.common.client.MediaRef;
import com.civileng.marketplace.web.common.client.MediaReferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProfileServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final MediaReferences media = mock(MediaReferences.class);
    private JwtTokenProvider jwt;
    private ProfileService service;
    private User user;

    @BeforeEach
    void setUp() {
        jwt = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwt, "secret", Base64.getEncoder()
                .encodeToString("test-secret-key-for-profile-service-tests-32b".getBytes()));
        ReflectionTestUtils.setField(jwt, "accessTokenExpiration", 900_000L);
        ReflectionTestUtils.setField(jwt, "refreshTokenExpiration", 2_592_000_000L);
        jwt.init();
        service = new ProfileService(jwt, users, media);

        Role role = new Role();
        role.setName("CUSTOMER");
        user = new User();
        user.setId(7L);
        user.setName("Asha");
        user.setEmail("asha@example.com");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TenantContext.set("acme");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private String bearer(String tenant) {
        return "Bearer " + jwt.generateAccessToken("7", "asha@example.com", "CUSTOMER", "Asha", tenant);
    }

    @Test
    void setsThePhotoToTheVerifiedUploadsUrl() {
        when(media.requireOwned("m1", "AVATAR", 7L)).thenReturn(
                new MediaRef("m1", "AVATAR", "PUBLIC", 7L, "me.png", "image/png", 10L, "http://cdn/me.png", null));

        AuthResponse.UserDto dto = service.setProfilePicture(bearer("acme"), "m1");

        assertThat(dto.getProfilePicture()).isEqualTo("http://cdn/me.png");
        assertThat(user.getProfilePicture()).isEqualTo("http://cdn/me.png");
    }

    @Test
    void aFileTheCheckRefusesChangesNothing() {
        when(media.requireOwned("m1", "AVATAR", 7L)).thenThrow(new IllegalArgumentException("Uploaded file not found"));
        assertThatThrownBy(() -> service.setProfilePicture(bearer("acme"), "m1"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(users, never()).save(any());
    }

    @Test
    void clears() {
        user.setProfilePicture("http://cdn/old.png");
        assertThat(service.clearProfilePicture(bearer("acme")).getProfilePicture()).isNull();
    }

    @Test
    void identityComesOnlyFromAValidAccessTokenForThisWorkspace() {
        assertThatThrownBy(() -> service.clearProfilePicture(null)).isInstanceOf(UnauthenticatedException.class);
        assertThatThrownBy(() -> service.clearProfilePicture("Bearer junk")).isInstanceOf(UnauthenticatedException.class);
        assertThatThrownBy(() -> service.clearProfilePicture(bearer("other")))
                .as("token from another workspace").isInstanceOf(UnauthenticatedException.class);
        assertThatThrownBy(() -> service.clearProfilePicture("Bearer " + jwt.generateRefreshToken("7", "acme")))
                .as("refresh token").isInstanceOf(UnauthenticatedException.class);

        String token = bearer("acme");
        jwt.blacklistToken(token.substring(7));
        assertThatThrownBy(() -> service.clearProfilePicture(token))
                .as("signed-out token").isInstanceOf(UnauthenticatedException.class);
        verify(users, never()).save(any());
    }
}
