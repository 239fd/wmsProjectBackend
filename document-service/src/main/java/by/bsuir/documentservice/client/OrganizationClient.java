package by.bsuir.documentservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationClient {

    private static final String BASE = "http://ORGANIZATION-SERVICE";

    private final RestTemplate loadBalancedRestTemplate;

    public Map<String, Object> getOrganization(UUID organizationId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            // Internal cross-service call: подкладываем DIRECTOR-роль и orgId,
            // иначе org-service вернёт 403 (RBAC по X-User-Role).
            headers.set("X-User-Role", "DIRECTOR");
            headers.set("X-Organization-Id", organizationId.toString());
            return loadBalancedRestTemplate.exchange(
                    BASE + "/api/organizations/" + organizationId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            ).getBody();
        } catch (Exception e) {
            log.warn("Не удалось получить организацию {}: {}", organizationId, e.getMessage());
            return new HashMap<>();
        }
    }
}
