package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.auth.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    private static final long REFRESH_TOKEN_EXPIRATION_DAYS = 30;

    public void storeRefreshToken(String userId, String refreshToken) {
        String key = "refresh_token:" + userId + ":" + refreshToken;
        redisTemplate.opsForValue().set(
                key,
                "valid",
                REFRESH_TOKEN_EXPIRATION_DAYS,
                TimeUnit.DAYS
        );
    }

    public boolean isValidRefreshToken(String userId, String refreshToken) {
        try {
            var claims = jwtTokenProvider.validateToken(refreshToken);
            if (!"refresh".equals(claims.get("type"))) {
                return false;
            }
            String key = "refresh_token:" + userId + ":" + refreshToken;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            return false;
        }
    }

    public void revokeRefreshToken(String userId, String refreshToken) {
        String key = "refresh_token:" + userId + ":" + refreshToken;
        redisTemplate.delete(key);
        log.info("Refresh token revoked for user: {}", userId);
    }

    public void revokeAllUserTokens(String userId) {
        String pattern = "refresh_token:" + userId + ":*";
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("All refresh tokens revoked for user: {}", userId);
        }
    }
}
