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
public class ProductClient {

    private static final String BASE = "http://PRODUCT-SERVICE";

    private final RestTemplate loadBalancedRestTemplate;

    public Map<String, Object> getProduct(UUID productId, UUID organizationId) {
        return get(BASE + "/api/products/" + productId, organizationId);
    }

    public Map<String, Object> getBatch(UUID batchId, UUID organizationId) {
        return get(BASE + "/api/batches/" + batchId, organizationId);
    }

    public Map<String, Object> getInventoryByCell(UUID cellId, UUID organizationId) {
        return get(BASE + "/api/inventory/cell/" + cellId, organizationId);
    }

    public Map<String, Object> getShipmentRequest(UUID requestId, UUID organizationId) {
        return get(BASE + "/api/operations/ship-requests/" + requestId, organizationId);
    }

    private Map<String, Object> get(String url, UUID organizationId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (organizationId != null) {
                headers.set("X-Organization-Id", organizationId.toString());
            }
            return loadBalancedRestTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            ).getBody();
        } catch (Exception e) {
            log.warn("Failed to fetch from {}: {}", url, e.getMessage());
            return new HashMap<>();
        }
    }
}
