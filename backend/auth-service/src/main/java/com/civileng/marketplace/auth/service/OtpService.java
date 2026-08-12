package com.civileng.marketplace.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class OtpService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${app.otp.length:6}")
    private int otpLength;

    @Value("${app.otp.resend-cooldown-seconds:30}")
    private int resendCooldownSeconds;

    public String generateAndStoreOtp(String email) {
        String cooldownKey = "otp:cooldown:" + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            long ttl = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
            throw new IllegalStateException(
                    "Please wait " + ttl + " seconds before requesting a new OTP");
        }

        String otp = generateOtp();
        String otpKey = "otp:" + email;

        redisTemplate.opsForValue().set(otpKey, otp, otpExpiryMinutes, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(cooldownKey, "1", resendCooldownSeconds, TimeUnit.SECONDS);

        log.info("OTP generated for {}: {}", email, otp);
        return otp;
    }

    public boolean validateOtp(String email, String otp) {
        String otpKey = "otp:" + email;
        String storedOtp = (String) redisTemplate.opsForValue().get(otpKey);

        if (storedOtp != null && storedOtp.equals(otp)) {
            redisTemplate.delete(otpKey);
            redisTemplate.delete("otp:cooldown:" + email);
            return true;
        }

        return false;
    }

    private String generateOtp() {
        StringBuilder otp = new StringBuilder(otpLength);
        for (int i = 0; i < otpLength; i++) {
            otp.append(secureRandom.nextInt(10));
        }
        return otp.toString();
    }
}
