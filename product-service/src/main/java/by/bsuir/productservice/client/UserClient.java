package by.bsuir.productservice.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserClient {

    private static final String BASE = "http://SSOSERVICE/api/internal/users";

    private final RestTemplate loadBalancedRestTemplate;

    public Map<UUID, String> resolveNames(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<String> ids = userIds.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(UUID::toString)
                    .distinct()
                    .collect(Collectors.toList());
            if (ids.isEmpty()) {
                return Collections.emptyMap();
            }
            ResponseEntity<Map<String, Map<String, Object>>> response = loadBalancedRestTemplate.exchange(
                    BASE + "/lookup",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("ids", ids)),
                    new ParameterizedTypeReference<>() {});
            Map<String, Map<String, Object>> body = response.getBody();
            if (body == null) {
                return Collections.emptyMap();
            }
            Map<UUID, String> result = new java.util.HashMap<>();
            body.forEach((id, info) -> {
                Object name = info.get("fullName");
                if (name != null) {
                    result.put(UUID.fromString(id), name.toString());
                }
            });
            return result;
        } catch (Exception e) {
            log.warn("Не удалось разрешить имена пользователей: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
