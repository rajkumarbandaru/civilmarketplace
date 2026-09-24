package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.auth.dto.AuthResponse;
import com.civileng.marketplace.auth.entity.Role;
import com.civileng.marketplace.auth.entity.User;
import com.civileng.marketplace.auth.entity.UserStatus;
import com.civileng.marketplace.auth.exception.UnauthenticatedException;
import com.civileng.marketplace.auth.repository.UserRepository;
import com.civileng.marketplace.auth.security.JwtTokenProvider;
import com.civileng.marketplace.auth.security.Totp;
import com.civileng.marketplace.tenant.common.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MfaServiceTest {

    private static final String SECRET_B64 = Base64.getEncoder()
            .encodeToString("test-secret-key-for-mfa-service-tests-32-bytes".getBytes());
    private static final long NOW = 1_790_000_000L;

    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenService refresh = mock(RefreshTokenService.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, Object> values = mock(ValueOperations.class);
    private final Map<String, Object> store = new HashMap<>();
    private final MfaSecretCipher cipher = new MfaSecretCipher(SECRET_B64);
    private JwtTokenProvider jwt;
    private MfaService mfa;
    private User admin;
    private long now = NOW;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jwt = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwt, "secret", SECRET_B64);
        ReflectionTestUtils.setField(jwt, "accessTokenExpiration", 900_000L);
        ReflectionTestUtils.setField(jwt, "refreshTokenExpiration", 2_592_000_000L);
        jwt.init();

        when(redis.opsForValue()).thenReturn(values);
        doAnswer(inv -> store.put(inv.getArgument(0), inv.getArgument(1))).when(values)
                .set(anyString(), any(), anyLong(), any(TimeUnit.class));
        when(values.get(anyString())).thenAnswer(inv -> store.get(inv.<String>getArgument(0)));
        when(values.increment(anyString())).thenAnswer(inv ->
                (Long) store.merge(inv.getArgument(0), 1L, (a, b) -> Long.parseLong(a.toString()) + 1));
        when(redis.hasKey(anyString())).thenAnswer(inv -> store.containsKey(inv.<String>getArgument(0)));
        when(redis.delete(anyString())).thenAnswer(inv -> store.remove(inv.<String>getArgument(0)) != null);
        when(redis.delete(anyCollection())).thenAnswer(inv -> {
            ((Collection<String>) inv.getArgument(0)).forEach(store::remove);
            return 1L;
        });

        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(inv -> Instant.ofEpochSecond(now));
        mfa = new MfaService(jwt, users, redis, cipher, new SessionIssuer(jwt, refresh), new ObjectMapper(), clock);

        Role role = new Role();
        role.setName("SUPER_ADMIN");
        admin = new User();
        admin.setId(1L);
        admin.setName("Ops");
        admin.setEmail("ops@example.com");
        admin.setRole(role);
        admin.setStatus(UserStatus.ACTIVE);
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TenantContext.set("platform");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** Runs the enrolment a first-time Super Admin goes through; returns the secret and recovery codes. */
    private AuthResponse enrol() {
        AuthResponse challenge = mfa.challenge(admin);
        String secret = mfa.setup(challenge.getMfaToken()).secret();
        store.put("test:secret", secret);
        return mfa.enable(challenge.getMfaToken(), Totp.code(secret, Totp.stepAt(now)));
    }

    private String secret() {
        return store.get("test:secret").toString();
    }

    @Test
    void superAdminsAlwaysNeedIt() {
        assertThat(mfa.required(admin)).isTrue();
        Role customer = new Role();
        customer.setName("CUSTOMER");
        User c = new User();
        c.setRole(customer);
        assertThat(mfa.required(c)).isFalse();
        c.setTwoFactorEnabled(true);
        assertThat(mfa.required(c)).as("opted in").isTrue();
    }

    @Test
    void anUnenrolledAdminIsSentToSetupAndGetsNoTokens() {
        AuthResponse r = mfa.challenge(admin);
        assertThat(r.getMfaRequired()).isTrue();
        assertThat(r.getMfaSetupRequired()).isTrue();
        assertThat(r.getAccessToken()).isNull();
        assertThat(r.getRefreshToken()).isNull();
    }

    @Test
    void enrolmentStoresTheSecretEncryptedAndCompletesSignIn() {
        AuthResponse session = enrol();
        assertThat(session.getAccessToken()).isNotBlank();
        assertThat(session.getRecoveryCodes()).hasSize(8).allMatch(c -> c.matches("[a-z2-9]{5}-[a-z2-9]{5}"));
        assertThat(admin.getTwoFactorEnabled()).isTrue();
        assertThat(admin.getTwoFactorSecret()).startsWith("v1:").doesNotContain(secret());
        assertThat(admin.getTwoFactorRecoveryCodes()).doesNotContain(session.getRecoveryCodes().get(0));
        verify(refresh).storeRefreshToken(eq("1"), anyString());
    }

    @Test
    void enrolmentNeedsARightCodeAndTheSetupTicket() {
        AuthResponse challenge = mfa.challenge(admin);
        mfa.setup(challenge.getMfaToken());
        assertThatThrownBy(() -> mfa.enable(challenge.getMfaToken(), "000000"))
                .hasMessageContaining("not right");
        assertThat(admin.getTwoFactorEnabled()).isFalse();
    }

    @Test
    void verifySignsInWithACurrentCodeOnce() {
        enrol();
        now += 30;
        AuthResponse challenge = mfa.challenge(admin);
        assertThat(challenge.getMfaSetupRequired()).isFalse();
        String code = Totp.code(secret(), Totp.stepAt(now));

        assertThat(mfa.verify(challenge.getMfaToken(), code).getAccessToken()).isNotBlank();
        assertThatThrownBy(() -> mfa.verify(challenge.getMfaToken(), code))
                .as("ticket is single use").isInstanceOf(UnauthenticatedException.class);
        assertThatThrownBy(() -> mfa.verify(mfa.challenge(admin).getMfaToken(), code))
                .as("code is single use").hasMessageContaining("not right");
    }

    @Test
    void aRecoveryCodeWorksOnceInsteadOfTheApp() {
        String recovery = enrol().getRecoveryCodes().get(3);
        assertThat(mfa.verify(mfa.challenge(admin).getMfaToken(), recovery.toUpperCase()).getAccessToken()).isNotBlank();
        assertThatThrownBy(() -> mfa.verify(mfa.challenge(admin).getMfaToken(), recovery))
                .hasMessageContaining("not right");
    }

    @Test
    void fiveWrongCodesLockTheSecondStepWhateverTicketIsUsed() {
        enrol();
        for (int i = 0; i < MfaService.MAX_FAILURES; i++) {
            String ticket = mfa.challenge(admin).getMfaToken();
            assertThatThrownBy(() -> mfa.verify(ticket, "000000")).hasMessageContaining("not right");
        }
        String right = Totp.code(secret(), Totp.stepAt(now + 30));
        now += 30;
        assertThatThrownBy(() -> mfa.verify(mfa.challenge(admin).getMfaToken(), right))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Too many");
    }

    @Test
    void ticketsAreBoundToPurposeTenantAndType() {
        String setupTicket = mfa.challenge(admin).getMfaToken();
        assertThatThrownBy(() -> mfa.verify(setupTicket, "123456")).isInstanceOf(UnauthenticatedException.class);

        enrol();
        String verifyTicket = mfa.challenge(admin).getMfaToken();
        assertThatThrownBy(() -> mfa.setup(verifyTicket)).isInstanceOf(UnauthenticatedException.class);

        TenantContext.set("acme");
        assertThatThrownBy(() -> mfa.verify(verifyTicket, "123456")).isInstanceOf(UnauthenticatedException.class);
        TenantContext.set("platform");

        String refreshToken = jwt.generateRefreshToken("1", "platform");
        assertThatThrownBy(() -> mfa.verify(refreshToken, "123456")).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void aSuspendedAccountCannotFinishSignIn() {
        enrol();
        String ticket = mfa.challenge(admin).getMfaToken();
        admin.setStatus(UserStatus.SUSPENDED);
        assertThatThrownBy(() -> mfa.verify(ticket, "123456")).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void theCipherBindsASecretToItsOwnerAndTenant() {
        String ct = cipher.encrypt("JBSWY3DPEHPK3PXP", "platform", 1L);
        assertThat(cipher.decrypt(ct, "platform", 1L)).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThatThrownBy(() -> cipher.decrypt(ct, "platform", 2L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(ct, "acme", 1L)).isInstanceOf(IllegalStateException.class);
    }
}
