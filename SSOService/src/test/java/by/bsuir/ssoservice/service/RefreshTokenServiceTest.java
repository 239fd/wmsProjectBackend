package by.bsuir.ssoservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService Unit Tests")
class RefreshTokenServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("saveRefreshToken: Should save token with correct key and TTL")
    void saveRefreshToken_ShouldSaveTokenWithCorrectKeyAndTtl() {
        String refreshToken = "test-refresh-token";
        UUID userId = UUID.randomUUID();
        Duration ttl = Duration.ofDays(7);

        refreshTokenService.saveRefreshToken(refreshToken, userId, ttl);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertThat(keyCaptor.getValue()).isEqualTo("refresh_token:test-refresh-token");
        assertThat(valueCaptor.getValue()).isEqualTo(userId.toString());
        assertThat(ttlCaptor.getValue()).isEqualTo(ttl);
    }

    @Test
    @DisplayName("getUserIdByRefreshToken: Given existing token Should return userId")
    void getUserIdByRefreshToken_GivenExistingToken_ShouldReturnUserId() {
        String refreshToken = "test-refresh-token";
        UUID userId = UUID.randomUUID();
        String key = "refresh_token:test-refresh-token";

        when(valueOperations.get(key)).thenReturn(userId.toString());

        UUID result = refreshTokenService.getUserIdByRefreshToken(refreshToken);

        assertThat(result).isEqualTo(userId);
        verify(valueOperations).get(key);
    }

    @Test
    @DisplayName("getUserIdByRefreshToken: Given non-existing token Should return null")
    void getUserIdByRefreshToken_GivenNonExistingToken_ShouldReturnNull() {
        String refreshToken = "non-existing-token";
        String key = "refresh_token:non-existing-token";

        when(valueOperations.get(key)).thenReturn(null);

        UUID result = refreshTokenService.getUserIdByRefreshToken(refreshToken);

        assertThat(result).isNull();
        verify(valueOperations).get(key);
    }

    @Test
    @DisplayName("deleteRefreshToken: Should delete token by key")
    void deleteRefreshToken_ShouldDeleteTokenByKey() {
        String refreshToken = "test-refresh-token";

        refreshTokenService.deleteRefreshToken(refreshToken);

        verify(redisTemplate).delete("refresh_token:test-refresh-token");
    }

    @Test
    @DisplayName("deleteAllUserTokens: Should delete all tokens belonging to user")
    void deleteAllUserTokens_ShouldDeleteAllTokensBelongingToUser() {
        UUID userId = UUID.randomUUID();
        String pattern = "refresh_token:*";

        String key1 = "refresh_token:token1";
        String key2 = "refresh_token:token2";
        String key3 = "refresh_token:token3";

        Set<String> keys = Set.of(key1, key2, key3);

        when(redisTemplate.keys(pattern)).thenReturn(keys);
        when(valueOperations.get(key1)).thenReturn(userId.toString());
        when(valueOperations.get(key2)).thenReturn(UUID.randomUUID().toString());
        when(valueOperations.get(key3)).thenReturn(userId.toString());

        refreshTokenService.deleteAllUserTokens(userId);

        verify(redisTemplate).keys(pattern);
        verify(redisTemplate).delete(key1);
        verify(redisTemplate).delete(key3);
        verify(redisTemplate, never()).delete(key2);
    }

    @Test
    @DisplayName("deleteAllUserTokens: Given no matching keys Should not delete anything")
    void deleteAllUserTokens_GivenNoMatchingKeys_ShouldNotDeleteAnything() {
        UUID userId = UUID.randomUUID();
        String pattern = "refresh_token:*";

        when(redisTemplate.keys(pattern)).thenReturn(null);

        refreshTokenService.deleteAllUserTokens(userId);

        verify(redisTemplate).keys(pattern);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("deleteAllUserTokens: Given empty keys Should not delete anything")
    void deleteAllUserTokens_GivenEmptyKeys_ShouldNotDeleteAnything() {
        UUID userId = UUID.randomUUID();
        String pattern = "refresh_token:*";

        when(redisTemplate.keys(pattern)).thenReturn(Set.of());

        refreshTokenService.deleteAllUserTokens(userId);

        verify(redisTemplate).keys(pattern);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("deleteAllUserTokens: Given no user tokens Should not delete anything")
    void deleteAllUserTokens_GivenNoUserTokens_ShouldNotDeleteAnything() {

        UUID userId = UUID.randomUUID();
        String pattern = "refresh_token:*";

        String key1 = "refresh_token:token1";
        String key2 = "refresh_token:token2";
        Set<String> keys = Set.of(key1, key2);

        when(redisTemplate.keys(pattern)).thenReturn(keys);
        when(valueOperations.get(key1)).thenReturn(UUID.randomUUID().toString());
        when(valueOperations.get(key2)).thenReturn(UUID.randomUUID().toString());

        refreshTokenService.deleteAllUserTokens(userId);

        verify(redisTemplate).keys(pattern);
        verify(redisTemplate, never()).delete(anyString());
    }
}

