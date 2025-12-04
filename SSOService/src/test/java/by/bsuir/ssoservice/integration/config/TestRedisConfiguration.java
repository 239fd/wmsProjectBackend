package by.bsuir.ssoservice.integration.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Тестовая конфигурация, заменяющая Redis на in-memory реализацию.
 * Используется для интеграционных тестов без запуска Redis.
 */
@TestConfiguration
public class TestRedisConfiguration {

    private final Map<String, Object> storage = new ConcurrentHashMap<>();

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = Mockito.mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = Mockito.mock(ValueOperations.class);

        when(template.opsForValue()).thenReturn(valueOperations);

        // Mock set operation
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object value = invocation.getArgument(1);
            storage.put(key, value);
            return null;
        }).when(valueOperations).set(anyString(), any());

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object value = invocation.getArgument(1);
            storage.put(key, value);
            return null;
        }).when(valueOperations).set(anyString(), any(), any(Duration.class));

        // Mock get operation
        when(valueOperations.get(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return storage.get(key);
        });

        // Mock delete operation
        when(template.delete(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return storage.remove(key) != null;
        });

        // Mock keys operation
        when(template.keys(anyString())).thenAnswer(invocation -> {
            String pattern = invocation.getArgument(0);
            String prefix = pattern.replace("*", "");
            Set<String> matchingKeys = new java.util.HashSet<>();
            for (String key : storage.keySet()) {
                if (key.startsWith(prefix)) {
                    matchingKeys.add(key);
                }
            }
            return matchingKeys;
        });

        return template;
    }
}

