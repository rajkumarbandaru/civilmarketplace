package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.tenant.common.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * One-time sign-in codes.
 *
 * <p>Keys carry the tenant: the same address can hold an account in two workspaces, and a code
 * issued in one must not sign anyone into the other. Wrong guesses are counted, and the code is
 * voided after {@link #MAX_ATTEMPTS} of them — otherwise six digits could be walked through inside
 * the expiry window. The code itself is never logged unless {@code app.otp.log-codes} is switched
 * on for a local stack with no mail or SMS configured.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OtpService {

    static final int MAX_ATTEMPTS = 5;

    private final RedisTemplate<String, Object> redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${app.otp.length:6}")
    private int otpLength;

    @Value("${app.otp.resend-cooldown-seconds:30}")
    private int resendCooldownSeconds;

    @Value("${app.otp.log-codes:false}")
    private boolean logCodes;

    public String generateAndStoreOtp(String email) {
        String cooldownKey = key("otp:cooldown:", email);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            long ttl = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
            throw new IllegalStateException(
                    "Please wait " + ttl + " seconds before requesting a new OTP");
        }

        String otp = generateOtp();
        String otpKey = key("otp:", email);

        redisTemplate.opsForValue().set(otpKey, otp, otpExpiryMinutes, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(cooldownKey, "1", resendCooldownSeconds, TimeUnit.SECONDS);
        redisTemplate.delete(key("otp:attempts:", email));

        if (logCodes) {
            log.warn("OTP for {}: {} (app.otp.log-codes is on — never enable this outside local dev)",
                    email, otp);
        } else {
            log.info("OTP generated for {}", mask(email));
        }
        return otp;
    }

    public boolean validateOtp(String email, String otp) {
        String otpKey = key("otp:", email);
        String attemptsKey = key("otp:attempts:", email);
        String storedOtp = (String) redisTemplate.opsForValue().get(otpKey);
        if (storedOtp == null) {
            return false;
        }

        if (MessageDigest.isEqual(storedOtp.getBytes(StandardCharsets.UTF_8),
                String.valueOf(otp).getBytes(StandardCharsets.UTF_8))) {
            redisTemplate.delete(List.of(otpKey, attemptsKey, key("otp:cooldown:", email)));
            return true;
        }

        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        redisTemplate.expire(attemptsKey, otpExpiryMinutes, TimeUnit.MINUTES);
        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            redisTemplate.delete(List.of(otpKey, attemptsKey));
            log.warn("OTP for {} voided after {} wrong attempts", mask(email), attempts);
        }
        return false;
    }

    private static String key(String prefix, String identifier) {
        return prefix + TenantContext.require() + ":" + identifier;
    }

    /** {@code asha@example.com} → {@code a***@example.com}; a phone keeps its last two digits. */
    static String mask(String identifier) {
        if (identifier == null || identifier.length() < 3) return "***";
        int at = identifier.indexOf('@');
        if (at > 0) return identifier.charAt(0) + "***" + identifier.substring(at);
        return "***" + identifier.substring(identifier.length() - 2);
    }

    private String generateOtp() {
        StringBuilder otp = new StringBuilder(otpLength);
        for (int i = 0; i < otpLength; i++) {
            otp.append(secureRandom.nextInt(10));
        }
        return otp.toString();
    }
}
