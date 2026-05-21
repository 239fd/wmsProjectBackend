package by.bsuir.warehouseservice.client;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductClient {

    private static final String CELLS_LOAD_URL = "http://PRODUCT-SERVICE/api/internal/inventory/cells-load";

    private final RestTemplate loadBalancedRestTemplate;

    public Map<UUID, CellLoad> getCellsLoad(List<UUID> cellIds) {
        if (cellIds == null || cellIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, CellLoad> result = new HashMap<>();
        try {
            List<Map<String, Object>> response = loadBalancedRestTemplate.exchange(
                    CELLS_LOAD_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("cellIds", cellIds)),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            ).getBody();
            if (response == null) {
                return result;
            }
            for (Map<String, Object> row : response) {
                Object cellIdObj = row.get("cellId");
                if (cellIdObj == null) {
                    continue;
                }
                UUID cellId = UUID.fromString(cellIdObj.toString());
                int itemsCount = ((Number) row.getOrDefault("itemsCount", 0)).intValue();
                BigDecimal totalQuantity = row.get("totalQuantity") == null
                        ? BigDecimal.ZERO
                        : new BigDecimal(row.get("totalQuantity").toString());
                boolean occupied = Boolean.TRUE.equals(row.get("occupied")) || itemsCount > 0;
                result.put(cellId, new CellLoad(itemsCount, totalQuantity, occupied));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch cells load from product-service: {}", e.getMessage());
        }
        return result;
    }

    public record CellLoad(int itemsCount, BigDecimal totalQuantity, boolean occupied) {
    }
}
