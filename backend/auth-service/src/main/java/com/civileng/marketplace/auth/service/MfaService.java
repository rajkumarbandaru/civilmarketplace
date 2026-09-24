package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.auth.dto.AuthResponse;
import com.civileng.marketplace.auth.dto.MfaSetupResponse;
import com.civileng.marketplace.auth.entity.User;
import com.civileng.marketplace.auth.entity.UserStatus;
import com.civileng.marketplace.auth.exception.UnauthenticatedException;
import com.civileng.marketplace.auth.repository.UserRepository;
import com.civileng.marketplace.auth.security.JwtTokenProvider;
import com.civileng.marketplace.auth.security.Totp;
import com.civileng.marketplace.tenant.common.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The second factor: an authenticator app (TOTP), required for SUPER_ADMIN.
 *
 * <p>Sign-in in two steps. Password, OTP or social sign-in ends in {@link #challenge} rather than a
 * session when the account needs MFA; the client then proves the code with the short-lived MFA
 * token it was given. An account that must have MFA and has none is walked through enrolment
 * ({@link #setup}, {@link #enable}) on that same token — there is no way to a session around it.
 *
 * <p>Guard rails: the secret is encrypted at rest; each code works once (the last accepted time
 * step is stored); a challenge token is single-use; five wrong codes lock the second step for
 * fifteen minutes per account, whatever token they came with, since whoever is guessing already
 * has the password and could fetch fresh tokens at will. Eight one-time recovery codes, stored
 * hashed, cover a lost phone.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MfaService {

    static final String VERIFY = "verify";
    static final String SETUP = "setup";
    static final int MAX_FAILURES = 5;
    static final int RECOVERY_CODES = 8;
    private static final long TOKEN_TTL_MS = 5 * 60 * 1000;
    private static final long LOCK_MINUTES = 15;
    private static final long PENDING_SECRET_MINUTES = 10;
    private static final Set<String> MFA_REQUIRED_ROLES = Set.of("SUPER_ADMIN");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String RECOVERY_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MfaSecretCipher cipher;
    private final SessionIssuer sessionIssuer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Value("${app.mfa.issuer:Civil Marketplace}")
    private String issuer = "Civil Marketplace";

    public boolean required(User user) {
        return MFA_REQUIRED_ROLES.contains(user.getRole().getName()) || Boolean.TRUE.equals(user.getTwoFactorEnabled());
    }

    public boolean enrolled(User user) {
        return Boolean.TRUE.equals(user.getTwoFactorEnabled()) && user.getTwoFactorSecret() != null;
    }

    /** What sign-in returns in place of a session when a second factor is due. */
    public AuthResponse challenge(User user) {
        boolean setup = !enrolled(user);
        String token = jwtTokenProvider.generateMfaToken(user.getId().toString(), TenantContext.require(),
                setup ? SETUP : VERIFY, TOKEN_TTL_MS);
        return AuthResponse.builder()
                .success(true)
                .message(setup ? "Set up an authenticator app to finish signing in"
                        : "Enter the code from your authenticator app")
                .mfaRequired(true)
                .mfaSetupRequired(setup)
                .mfaToken(token)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /** A secret to enrol, held unconfirmed until {@link #enable} proves the app has it. */
    public MfaSetupResponse setup(String mfaToken) {
        User user = userFor(mfaToken, SETUP, false);
        if (enrolled(user)) {
            throw new IllegalArgumentException("An authenticator is already set up for this account");
        }
        String key = pendingKey(user);
        Object pending = redisTemplate.opsForValue().get(key);
        String secret = pending != null ? pending.toString() : Totp.newSecret();
        // The same secret while the slot lives, so reloading the page does not orphan a scan.
        redisTemplate.opsForValue().set(key, secret, PENDING_SECRET_MINUTES, TimeUnit.MINUTES);
        String account = user.getEmail() != null ? user.getEmail() : user.getPhone();
        return new MfaSetupResponse(secret, Totp.otpauthUri(issuer + " (" + TenantContext.require() + ")", account, secret));
    }

    /** Confirms enrolment with a first code, and completes the sign-in it interrupted. */
    @Transactional
    public AuthResponse enable(String mfaToken, String code) {
        User user = userFor(mfaToken, SETUP, false);
        requireNotLocked(user);
        Object pending = redisTemplate.opsForValue().get(pendingKey(user));
        if (pending == null) {
            throw new IllegalArgumentException("Setup expired. Start again.");
        }
        long step = Totp.matchingStep(pending.toString(), normalise(code), clock.instant().getEpochSecond(), null);
        if (step < 0) {
            fail(user);
        }
        List<String> recovery = newRecoveryCodes();
        user.setTwoFactorSecret(cipher.encrypt(pending.toString(), TenantContext.require(), user.getId()));
        user.setTwoFactorEnabled(true);
        user.setTwoFactorLastStep(step);
        user.setTwoFactorRecoveryCodes(write(recovery.stream().map(MfaService::hash).toList()));
        userRepository.save(user);
        redisTemplate.delete(List.of(pendingKey(user), failuresKey(user)));
        spend(mfaToken);
        log.info("User {} enrolled an authenticator app", user.getId());

        AuthResponse session = sessionIssuer.issue(user, "Two-step sign-in is on");
        session.setRecoveryCodes(recovery);
        return session;
    }

    /** The second step of sign-in: an authenticator code, or one of the recovery codes. */
    @Transactional
    public AuthResponse verify(String mfaToken, String code) {
        User user = userFor(mfaToken, VERIFY, true);
        requireNotLocked(user);
        String secret = cipher.decrypt(user.getTwoFactorSecret(), TenantContext.require(), user.getId());
        String entered = normalise(code);

        long step = Totp.matchingStep(secret, entered, clock.instant().getEpochSecond(), user.getTwoFactorLastStep());
        if (step >= 0) {
            user.setTwoFactorLastStep(step);
        } else if (!useRecoveryCode(user, entered)) {
            fail(user);
        }
        userRepository.save(user);
        redisTemplate.delete(failuresKey(user));
        spend(mfaToken);
        return sessionIssuer.issue(user, "Login successful");
    }

    private User userFor(String mfaToken, String purpose, boolean mustBeEnrolled) {
        Claims claims;
        try {
            claims = jwtTokenProvider.validateToken(mfaToken);
        } catch (RuntimeException e) {
            throw new UnauthenticatedException("This sign-in has expired. Sign in again.");
        }
        if (!"mfa".equals(claims.get("type", String.class))
                || !purpose.equals(claims.get("purpose", String.class))
                || !TenantContext.require().equals(claims.get("tenant", String.class))
                || Boolean.TRUE.equals(redisTemplate.hasKey(spentKey(claims.getId())))) {
            throw new UnauthenticatedException("This sign-in has expired. Sign in again.");
        }
        User user = userRepository.findById(Long.parseLong(claims.getSubject()))
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .filter(u -> u.getStatus() != UserStatus.SUSPENDED && u.getStatus() != UserStatus.BANNED)
                .orElseThrow(() -> new UnauthenticatedException("This sign-in has expired. Sign in again."));
        if (mustBeEnrolled && !enrolled(user)) {
            throw new UnauthenticatedException("This sign-in has expired. Sign in again.");
        }
        return user;
    }

    private void spend(String mfaToken) {
        Claims claims = jwtTokenProvider.validateToken(mfaToken);
        redisTemplate.opsForValue().set(spentKey(claims.getId()), "1", TOKEN_TTL_MS, TimeUnit.MILLISECONDS);
    }

    private void requireNotLocked(User user) {
        Object failures = redisTemplate.opsForValue().get(failuresKey(user));
        if (failures != null && Long.parseLong(failures.toString()) >= MAX_FAILURES) {
            throw new IllegalStateException("Too many wrong codes. Try again in " + LOCK_MINUTES + " minutes.");
        }
    }

    private void fail(User user) {
        Long failures = redisTemplate.opsForValue().increment(failuresKey(user));
        redisTemplate.expire(failuresKey(user), LOCK_MINUTES, TimeUnit.MINUTES);
        log.warn("Wrong MFA code for user {} ({} of {})", user.getId(), failures, MAX_FAILURES);
        throw new IllegalArgumentException("That code is not right. Check your authenticator app and try again.");
    }

    private boolean useRecoveryCode(User user, String entered) {
        if (entered == null || user.getTwoFactorRecoveryCodes() == null) return false;
        List<String> hashes = new ArrayList<>(read(user.getTwoFactorRecoveryCodes()));
        String candidate = hash(entered.replace("-", ""));
        boolean found = hashes.removeIf(h -> MessageDigest.isEqual(
                h.getBytes(StandardCharsets.US_ASCII), candidate.getBytes(StandardCharsets.US_ASCII)));
        if (found) {
            user.setTwoFactorRecoveryCodes(write(hashes));
            log.warn("User {} signed in with a recovery code ({} left)", user.getId(), hashes.size());
        }
        return found;
    }

    static List<String> newRecoveryCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < 10; c++) {
                if (c == 5) sb.append('-');
                sb.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    static String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(code.replace("-", "").toLowerCase().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String normalise(String code) {
        return code == null ? null : code.replace(" ", "").trim().toLowerCase();
    }

    private String write(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> read(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pendingKey(User user) {
        return "mfa:pending:" + TenantContext.require() + ":" + user.getId();
    }

    private static String failuresKey(User user) {
        return "mfa:failures:" + TenantContext.require() + ":" + user.getId();
    }

    private static String spentKey(String jti) {
        return "mfa:spent:" + TenantContext.require() + ":" + jti;
    }
}
