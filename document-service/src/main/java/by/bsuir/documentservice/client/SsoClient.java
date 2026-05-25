package by.bsuir.documentservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SsoClient {

    private static final String BASE = "http://SSOSERVICE";

    private final RestTemplate loadBalancedRestTemplate;

    public Map<String, Map<String, Object>> lookupUsers(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, List<String>> body = new HashMap<>();
            body.put("ids", userIds.stream().map(UUID::toString).collect(Collectors.toList()));

            return loadBalancedRestTemplate.exchange(
                    BASE + "/api/internal/users/lookup",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<Map<String, Map<String, Object>>>() {}
            ).getBody();
        } catch (Exception e) {
            log.warn("Не удалось получить пользователей через SSO lookup ({}): {}", userIds.size(), e.getMessage());
            return new HashMap<>();
        }
    }
}
