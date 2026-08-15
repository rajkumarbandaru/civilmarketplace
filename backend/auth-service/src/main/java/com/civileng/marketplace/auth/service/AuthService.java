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

    /** Roles that can only ever be granted by an administrator, never self-selected. */
    private static final Set<String> PRIVILEGED_ROLES = Set.of(
            "SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN", "CITY_MANAGER");

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 30;

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

        return buildAuthResponse(user, "Login successful");
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

        return buildAuthResponse(user, "OTP verification successful");
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
