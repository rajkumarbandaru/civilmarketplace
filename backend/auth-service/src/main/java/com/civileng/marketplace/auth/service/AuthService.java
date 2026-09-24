package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.auth.dto.*;
import com.civileng.marketplace.auth.entity.Role;
import com.civileng.marketplace.auth.entity.User;
import com.civileng.marketplace.auth.entity.UserStatus;
import com.civileng.marketplace.auth.repository.RoleRepository;
import com.civileng.marketplace.auth.repository.UserRepository;
import com.civileng.marketplace.auth.exception.InvalidRefreshTokenException;
import com.civileng.marketplace.auth.security.JwtTokenProvider;
import com.civileng.marketplace.tenant.common.TenantContext;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final AccountIdentifiers identifiers;
    private final SessionIssuer sessionIssuer;
    private final MfaService mfaService;

    /** Roles that can only ever be granted by an administrator, never self-selected. */
    private static final Set<String> PRIVILEGED_ROLES = Set.of(
            "SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN", "CITY_MANAGER");

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 30;
    private static final long DEVICE_TOKEN_WINDOW_MS = 2 * 60 * 1000;
    private static final Set<UserStatus> BLOCKED_STATUSES =
            Set.of(UserStatus.SUSPENDED, UserStatus.BANNED, UserStatus.DELETED);

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Normalise before checking: the check is only as good as the canonical form, and
        // `+91 94935 64235` must collide with an existing `+919493564235`.
        String email = identifiers.normaliseEmail(request.getEmail());
        String phone = identifiers.normalisePhone(request.getPhone());

        if (userRepository.existsByEmailAndIsDeletedFalse(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        if (phone != null && userRepository.existsByPhoneAndIsDeletedFalse(phone)) {
            throw new IllegalArgumentException("Phone number already registered");
        }

        String roleName = request.getRole() != null ?
                request.getRole().toUpperCase() : "CUSTOMER";

        // Self-registration must never grant an administrative role: the role name
        // arrives straight from the request body, so without this check anyone could
        // sign up as SUPER_ADMIN. Admin roles are assigned from the admin console only.
        if (PRIVILEGED_ROLES.contains(roleName)) {
            log.warn("Rejected self-registration attempt for privileged role {} by {}",
                    roleName, request.getEmail());
            throw new IllegalArgumentException("Invalid role: " + roleName);
        }

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid role: " + roleName));

        User user = User.builder()
                .name(request.getName())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(phone)
                .role(role)
                .status(UserStatus.PENDING_VERIFICATION)
                .build();

        user = userRepository.save(user);

        log.info("User registered successfully: {}", user.getEmail());

        kafkaTemplate.send("user.registered",
                Map.of("userId", user.getId(), "email", user.getEmail(),
                        "name", user.getName(), "phone", user.getPhone()));

        // Verification is driven by the same OTP machinery as OTP sign-in, so a new account
        // can be verified over whichever channel the user picked without a second code path.
        OtpChannel channel = OtpChannel.parse(request.getVerificationChannel(), OtpChannel.EMAIL);
        dispatchOtp(user, channel);

        return buildAuthResponse(user, "Registration successful. A verification code has been sent "
                + (channel.usesPhone() ? "to your mobile number." : "to your email address."));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository
                .findByEmailAndIsDeletedFalse(identifiers.normaliseEmail(request.getEmail()))
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

        return completeSignIn(user, "Login successful");
    }

    public AuthResponse sendOtp(OtpRequest request) {
        boolean byPhone = isPhoneLogin(request.getEmail(), request.getPhone());
        User user = resolveOtpUser(request.getEmail(), request.getPhone(), byPhone);

        OtpChannel channel = OtpChannel.parse(
                request.getChannel(), byPhone ? OtpChannel.SMS : OtpChannel.EMAIL);
        if (channel.usesPhone() != byPhone) {
            throw new IllegalArgumentException(channel.usesPhone()
                    ? "Channel " + channel + " requires a mobile number"
                    : "Channel EMAIL requires an email address");
        }

        dispatchOtp(user, channel);

        return AuthResponse.builder()
                .success(true)
                .message("OTP sent over " + channel)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Generates a code and hands delivery to notification-service over Kafka.
     *
     * <p>The code is keyed on the user id, not the submitted identifier, so a code requested
     * for one identifier cannot be replayed against the other. Auth never talks to an email
     * or SMS provider itself.
     */
    private void dispatchOtp(User user, OtpChannel channel) {
        String otp = otpService.generateAndStoreOtp(otpKey(user, channel));

        Map<String, Object> event = channel.usesPhone()
                ? Map.of("channel", channel.name(), "phone", user.getPhone(), "otp", otp)
                : Map.of("channel", channel.name(), "email", user.getEmail(), "otp", otp);
        kafkaTemplate.send("otp.sent", event);

        log.info("OTP sent to user {} over {}", user.getId(), channel);
    }

    @Transactional
    public AuthResponse verifyOtpAndLogin(OtpVerifyRequest request) {
        boolean byPhone = isPhoneLogin(request.getEmail(), request.getPhone());
        // The identifier alone determines the key: SMS and WhatsApp codes go to the same
        // number and share a key, so a verify call needs no channel of its own.
        OtpChannel channel = byPhone ? OtpChannel.SMS : OtpChannel.EMAIL;
        User user = resolveOtpUser(request.getEmail(), request.getPhone(), byPhone);

        if (!otpService.validateOtp(otpKey(user, channel), request.getOtp())) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }

        // Receiving the code only proves control of the channel it was sent to.
        if (byPhone) {
            user.setPhoneVerified(true);
        } else {
            user.setEmailVerified(true);
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);
        userRepository.updateLastLogin(user.getId(), LocalDateTime.now());

        log.info("User logged in via OTP: {}", user.getEmail());

        return completeSignIn(user, "OTP verification successful");
    }

    /** True when the request identifies the account by mobile number rather than email. */
    private boolean isPhoneLogin(String email, String phone) {
        return (email == null || email.isBlank()) && phone != null && !phone.isBlank();
    }

    /**
     * Looks up the account an OTP request refers to.
     *
     * The "not registered" wording is deliberately identical for both channels and
     * reveals nothing beyond what the caller already supplied.
     */
    private User resolveOtpUser(String email, String phone, boolean byPhone) {
        if (byPhone) {
            return userRepository.findByPhoneAndIsDeletedFalse(identifiers.normalisePhone(phone))
                    .orElseThrow(() ->
                            new IllegalArgumentException("Mobile number not registered"));
        }
        return userRepository.findByEmailAndIsDeletedFalse(identifiers.normaliseEmail(email))
                .orElseThrow(() -> new IllegalArgumentException("Email not registered"));
    }

    private String otpKey(User user, OtpChannel channel) {
        return channel.keyPrefix() + user.getId();
    }

    /**
     * Exchanges a refresh token for a new pair, rotating it. See {@link RefreshTokenService} for
     * what each outcome means; a replayed token revokes every session the user has.
     */
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshClaims token = parseRefreshToken(request.getRefreshToken());

        switch (refreshTokenService.rotate(token.userId(), request.getRefreshToken())) {
            case ROTATED, GRACE -> { }
            case REUSED -> {
                log.warn("Refresh token reuse detected for user {}; revoking all sessions", token.userId());
                refreshTokenService.revokeAllUserTokens(token.userId());
                throw new InvalidRefreshTokenException();
            }
            default -> throw new InvalidRefreshTokenException();
        }

        User user = activeUser(token.userId());
        // A session from before this account had to have a second factor cannot outlive the rule:
        // it is refused, and the next sign-in goes through enrolment.
        if (mfaService.required(user) && !mfaService.enrolled(user)) {
            throw new InvalidRefreshTokenException();
        }
        return buildAuthResponse(user, "Token refreshed successfully");
    }

    /**
     * A second, independent refresh token for "keep me signed in on this device".
     *
     * <p>The remembered token and the tab's own token must never be the same token: whichever
     * rotated it first would leave the other holding a spent token, and presenting that later reads
     * as theft and signs the user out everywhere. So the device gets its own chain.
     *
     * <p>Only a token minted in the last {@link #DEVICE_TOKEN_WINDOW_MS} can be forked. Forking
     * starts a chain that reuse detection cannot link back, so leaving it open to any live token
     * would let someone who stole one later fork a private copy and never trip the alarm.
     */
    public AuthResponse issueDeviceToken(RefreshTokenRequest request) {
        RefreshClaims token = parseRefreshToken(request.getRefreshToken());
        if (!refreshTokenService.isValidRefreshToken(token.userId(), request.getRefreshToken())
                || System.currentTimeMillis() - token.issuedAt() > DEVICE_TOKEN_WINDOW_MS) {
            throw new InvalidRefreshTokenException();
        }
        activeUser(token.userId());

        String deviceToken = jwtTokenProvider.generateRefreshToken(token.userId(), TenantContext.require());
        refreshTokenService.storeRefreshToken(token.userId(), deviceToken);
        return AuthResponse.builder()
                .success(true)
                .message("Device token issued")
                .refreshToken(deviceToken)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Ends the session on the server, not only in the browser: the access token is blacklisted and
     * each refresh token given (the tab's and the remembered device's) is revoked. Tokens that do
     * not parse are skipped, so signing out always succeeds for the caller.
     */
    @Transactional
    public void logout(String accessToken, List<String> refreshTokens) {
        if (accessToken != null && !accessToken.isBlank()) {
            jwtTokenProvider.blacklistToken(accessToken);
        }
        if (refreshTokens != null) {
            for (String refreshToken : refreshTokens) {
                if (refreshToken == null || refreshToken.isBlank()) continue;
                try {
                    String userId = jwtTokenProvider.validateToken(refreshToken).getSubject();
                    refreshTokenService.revokeRefreshToken(userId, refreshToken);
                } catch (Exception e) {
                    // Already expired or malformed: nothing left to revoke.
                }
            }
        }
        log.info("User logged out, tokens revoked");
    }

    private RefreshClaims parseRefreshToken(String refreshToken) {
        Claims claims;
        try {
            claims = jwtTokenProvider.validateToken(refreshToken);
        } catch (Exception e) {
            throw new InvalidRefreshTokenException();
        }
        // A refresh token is only good on the workspace it was issued for — the gateway checks this
        // for access tokens, but the auth routes bypass that filter.
        if (!"refresh".equals(claims.get("type"))
                || !TenantContext.require().equals(claims.get("tenant", String.class))) {
            throw new InvalidRefreshTokenException();
        }
        long issuedAt = claims.getIssuedAt() != null ? claims.getIssuedAt().getTime() : 0L;
        return new RefreshClaims(claims.getSubject(), issuedAt);
    }

    /** A suspended, banned or deleted account loses every session the moment it next refreshes. */
    private User activeUser(String userId) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (Boolean.TRUE.equals(user.getIsDeleted()) || BLOCKED_STATUSES.contains(user.getStatus())) {
            refreshTokenService.revokeAllUserTokens(userId);
            throw new InvalidRefreshTokenException();
        }
        return user;
    }

    private record RefreshClaims(String userId, long issuedAt) { }

    private void handleFailedLogin(User user) {
        userRepository.incrementLoginAttempts(user.getId());

        if (user.getLoginAttempts() + 1 >= MAX_LOGIN_ATTEMPTS) {
            userRepository.lockAccount(user.getId(),
                    LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
            log.warn("Account locked due to multiple failed attempts: {}", user.getEmail());
        }
    }

    private AuthResponse buildAuthResponse(User user, String message) {
        return sessionIssuer.issue(user, message);
    }

    /**
     * The end of every interactive sign-in: a session, or — for an account that needs a second
     * factor — an MFA challenge in its place. Refresh does not come through here: its session was
     * already granted with every factor.
     */
    private AuthResponse completeSignIn(User user, String message) {
        return mfaService.required(user) ? mfaService.challenge(user) : sessionIssuer.issue(user, message);
    }
}
