package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.auth.dto.AuthResponse;
import com.civileng.marketplace.auth.dto.RefreshTokenRequest;
import com.civileng.marketplace.auth.entity.Role;
import com.civileng.marketplace.auth.entity.User;
import com.civileng.marketplace.auth.entity.UserStatus;
import com.civileng.marketplace.auth.exception.InvalidRefreshTokenException;
import com.civileng.marketplace.auth.repository.RoleRepository;
import com.civileng.marketplace.auth.repository.UserRepository;
import com.civileng.marketplace.auth.security.JwtTokenProvider;
import com.civileng.marketplace.auth.service.RefreshTokenService.Outcome;
import com.civileng.marketplace.tenant.common.TenantContext;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("AuthService - refresh rotation, device tokens and logout")
class AuthServiceRefreshTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("test-secret-key-for-refresh-rotation-tests-32b".getBytes());

    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenService store = mock(RefreshTokenService.class);
    private final MfaService mfa = mock(MfaService.class);
    private JwtTokenProvider jwt;
    private AuthService service;
    private User user;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jwt = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwt, "secret", SECRET);
        ReflectionTestUtils.setField(jwt, "accessTokenExpiration", 900_000L);
        ReflectionTestUtils.setField(jwt, "refreshTokenExpiration", 2_592_000_000L);
        jwt.init();

        service = new AuthService(users, mock(RoleRepository.class), mock(PasswordEncoder.class), jwt,
                mock(OtpService.class), store, mock(KafkaTemplate.class), mock(AccountIdentifiers.class),
                new SessionIssuer(jwt, store), mfa);

        Role role = new Role();
        role.setName("CUSTOMER");
        user = new User();
        user.setId(7L);
        user.setName("Asha");
        user.setEmail("asha@example.com");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        TenantContext.set("acme");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static RefreshTokenRequest req(String token) {
        RefreshTokenRequest r = new RefreshTokenRequest();
        r.setRefreshToken(token);
        return r;
    }

    @Test
    void validTokenIsRotatedAndANewDistinctOneIssued() {
        String token = jwt.generateRefreshToken("7", "acme");
        when(store.rotate("7", token)).thenReturn(Outcome.ROTATED);

        AuthResponse response = service.refreshToken(req(token));

        assertThat(response.getRefreshToken()).isNotEqualTo(token);
        assertThat(response.getAccessToken()).isNotBlank();
        verify(store).storeRefreshToken("7", response.getRefreshToken());
    }

    @Test
    void twoTokensMintedTogetherDiffer() {
        assertThat(jwt.generateRefreshToken("7", "acme")).isNotEqualTo(jwt.generateRefreshToken("7", "acme"));
    }

    @Test
    void benignRaceWithinGraceStillSucceeds() {
        String token = jwt.generateRefreshToken("7", "acme");
        when(store.rotate("7", token)).thenReturn(Outcome.GRACE);
        assertThat(service.refreshToken(req(token)).getRefreshToken()).isNotBlank();
        verify(store, never()).revokeAllUserTokens(any());
    }

    @Test
    void replayedTokenRevokesEverySession() {
        String token = jwt.generateRefreshToken("7", "acme");
        when(store.rotate("7", token)).thenReturn(Outcome.REUSED);
        assertThatThrownBy(() -> service.refreshToken(req(token))).isInstanceOf(InvalidRefreshTokenException.class);
        verify(store).revokeAllUserTokens("7");
        verify(store, never()).storeRefreshToken(any(), any());
    }

    @Test
    void revokedTokenIsRefusedWithoutRevokingOthers() {
        String token = jwt.generateRefreshToken("7", "acme");
        when(store.rotate("7", token)).thenReturn(Outcome.UNKNOWN);
        assertThatThrownBy(() -> service.refreshToken(req(token))).isInstanceOf(InvalidRefreshTokenException.class);
        verify(store, never()).revokeAllUserTokens(any());
    }

    @Test
    void tokenFromAnotherWorkspaceIsRefused() {
        String token = jwt.generateRefreshToken("7", "other-tenant");
        assertThatThrownBy(() -> service.refreshToken(req(token))).isInstanceOf(InvalidRefreshTokenException.class);
        verify(store, never()).rotate(any(), any());
    }

    @Test
    void accessTokenCannotBeUsedAsRefreshToken() {
        String access = jwt.generateAccessToken("7", "a@b.c", "CUSTOMER", "Asha", "acme");
        assertThatThrownBy(() -> service.refreshToken(req(access))).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void garbageIsA401NotA500() {
        assertThatThrownBy(() -> service.refreshToken(req("not-a-jwt"))).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void suspendedUserLosesAllSessionsOnRefresh() {
        user.setStatus(UserStatus.SUSPENDED);
        String token = jwt.generateRefreshToken("7", "acme");
        when(store.rotate("7", token)).thenReturn(Outcome.ROTATED);
        assertThatThrownBy(() -> service.refreshToken(req(token))).isInstanceOf(InvalidRefreshTokenException.class);
        verify(store).revokeAllUserTokens("7");
    }

    @Test
    void deviceTokenIsIndependentAndOnlyForFreshTokens() {
        String fresh = jwt.generateRefreshToken("7", "acme");
        when(store.isValidRefreshToken("7", fresh)).thenReturn(true);

        AuthResponse device = service.issueDeviceToken(req(fresh));

        assertThat(device.getRefreshToken()).isNotEqualTo(fresh);
        assertThat(device.getAccessToken()).isNull();
        verify(store).storeRefreshToken("7", device.getRefreshToken());
        verify(store, never()).rotate(any(), any());
    }

    @Test
    void deviceTokenRefusedForOldOrSpentTokens() {
        byte[] key = Base64.getDecoder().decode(SECRET);
        String old = Jwts.builder().subject("7").claim("type", "refresh").claim("tenant", "acme")
                .issuedAt(new Date(System.currentTimeMillis() - 10 * 60_000))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(key)).compact();
        when(store.isValidRefreshToken("7", old)).thenReturn(true);
        assertThatThrownBy(() -> service.issueDeviceToken(req(old))).isInstanceOf(InvalidRefreshTokenException.class);

        String spent = jwt.generateRefreshToken("7", "acme");
        when(store.isValidRefreshToken("7", spent)).thenReturn(false);
        assertThatThrownBy(() -> service.issueDeviceToken(req(spent))).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutRevokesEveryTokenGivenAndSkipsJunk() {
        String tab = jwt.generateRefreshToken("7", "acme");
        String device = jwt.generateRefreshToken("7", "acme");
        String access = jwt.generateAccessToken("7", "a@b.c", "CUSTOMER", "Asha", "acme");

        service.logout(access, List.of(tab, device, "junk"));

        verify(store).revokeRefreshToken("7", tab);
        verify(store).revokeRefreshToken("7", device);
        assertThatThrownBy(() -> jwt.validateToken(access)).hasMessageContaining("revoked");
    }

    @Test
    @DisplayName("a Super Admin session from before MFA was required cannot be refreshed until they enrol")
    void refreshRefusedForAnAccountThatNeedsMfaButHasNone() {
        String refresh = jwt.generateRefreshToken("7", "acme");
        when(store.rotate("7", refresh)).thenReturn(RefreshTokenService.Outcome.ROTATED);
        when(mfa.required(user)).thenReturn(true);
        when(mfa.enrolled(user)).thenReturn(false);
        assertThatThrownBy(() -> service.refreshToken(req(refresh)))
                .isInstanceOf(com.civileng.marketplace.auth.exception.InvalidRefreshTokenException.class);

        when(mfa.enrolled(user)).thenReturn(true);
        assertThat(service.refreshToken(req(refresh)).getAccessToken()).isNotBlank();
    }
}
