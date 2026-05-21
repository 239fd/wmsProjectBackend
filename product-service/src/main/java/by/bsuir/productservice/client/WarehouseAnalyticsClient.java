package by.bsuir.productservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseAnalyticsClient {

    private static final String BASE = "http://WAREHOUSE-SERVICE/api/warehouses/analytics";

    private final RestTemplate loadBalancedRestTemplate;

    public Map<String, Object> getOrganizationSummary(UUID orgId, String userRole) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (userRole != null) headers.set("X-User-Role", userRole);
            ResponseEntity<Map<String, Object>> response = loadBalancedRestTemplate.exchange(
                    BASE + "/organization/" + orgId + "/summary",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> body = response.getBody();
            return body != null ? body : Collections.emptyMap();
        } catch (Exception e) {
            log.error("Failed to fetch warehouses summary for org {}: {}", orgId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    public Map<String, Object> getWarehouseAnalytics(UUID warehouseId, String userRole) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (userRole != null) headers.set("X-User-Role", userRole);
            ResponseEntity<Map<String, Object>> response = loadBalancedRestTemplate.exchange(
                    BASE + "/" + warehouseId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> body = response.getBody();
            return body != null ? body : Collections.emptyMap();
        } catch (Exception e) {
            log.error("Failed to fetch warehouse analytics {}: {}", warehouseId, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
