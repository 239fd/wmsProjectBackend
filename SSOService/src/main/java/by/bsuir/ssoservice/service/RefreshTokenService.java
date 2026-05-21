package by.bsuir.ssoservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";

    public void saveRefreshToken(String refreshToken, UUID userId, Duration ttl) {
        String key = REFRESH_TOKEN_PREFIX + refreshToken;
        redisTemplate.opsForValue().set(key, userId.toString(), ttl);
        log.debug("Refresh token saved for user: {}", userId);
    }

    public UUID getUserIdByRefreshToken(String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + refreshToken;
        Object userId = redisTemplate.opsForValue().get(key);
        if (userId != null) {
            return UUID.fromString(userId.toString());
        }
        return null;
    }

    public void deleteRefreshToken(String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + refreshToken;
        redisTemplate.delete(key);
        log.debug("Refresh token deleted: {}", refreshToken);
    }

    public void deleteAllUserTokens(UUID userId) {
        deleteAllUserTokensExcept(userId, null);
    }

    public boolean deleteUserTokenByHash(UUID userId, String hash, PasswordEncoder passwordEncoder) {
        if (hash == null || hash.isBlank()) {
            return false;
        }
        String pattern = REFRESH_TOKEN_PREFIX + "*";
        var keys = redisTemplate.keys(pattern);
        if (keys == null) {
            return false;
        }
        for (String key : keys) {
            try {
                Object storedUserId = redisTemplate.opsForValue().get(key);
                if (!userId.toString().equals(storedUserId)) {
                    continue;
                }
                String plainToken = key.substring(REFRESH_TOKEN_PREFIX.length());
                if (passwordEncoder.matches(plainToken, hash)) {
                    redisTemplate.delete(key);
                    log.debug("Refresh token matched and deleted for user {}", userId);
                    return true;
                }
            } catch (Exception ex) {
                log.debug("deleteUserTokenByHash: skipping key {} due to {}", key, ex.getMessage());
            }
        }
        return false;
    }

    public void deleteAllUserTokensExcept(UUID userId, String exceptToken) {
        String pattern = REFRESH_TOKEN_PREFIX + "*";
        var keys = redisTemplate.keys(pattern);
        if (keys == null) {
            return;
        }
        String exceptKey = exceptToken == null ? null : REFRESH_TOKEN_PREFIX + exceptToken;
        keys.forEach(key -> {
            if (key.equals(exceptKey)) {
                return;
            }
            Object storedUserId = redisTemplate.opsForValue().get(key);
            if (userId.toString().equals(storedUserId)) {
                redisTemplate.delete(key);
            }
        });
        log.debug("All tokens deleted for user: {} (kept: {})", userId, exceptToken == null ? "none" : "current");
    }
}
