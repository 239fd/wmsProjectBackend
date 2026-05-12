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
public class WarehouseClient {

    private static final String BASE = "http://WAREHOUSE-SERVICE";

    private final RestTemplate loadBalancedRestTemplate;

    public Map<String, Object> getWarehouse(UUID warehouseId) {
        try {
            return loadBalancedRestTemplate.exchange(
                    BASE + "/api/warehouses/" + warehouseId,
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            ).getBody();
        } catch (Exception e) {
            log.warn("Failed to fetch warehouse {}: {}", warehouseId, e.getMessage());
            return new HashMap<>();
        }
    }
}
