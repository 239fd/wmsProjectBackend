package by.bsuir.productservice.client;

import by.bsuir.productservice.client.dto.CellInfoDto;
import by.bsuir.productservice.client.dto.PageResponse;
import by.bsuir.productservice.client.dto.RackInfoDto;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseClient {

    private static final String BASE = "http://WAREHOUSE-SERVICE/api/racks";

    private final RestTemplate loadBalancedRestTemplate;

    public List<RackInfoDto> getRacksByWarehouse(UUID warehouseId, String userRole) {
        try {
            HttpHeaders headers = buildHeaders(userRole);
            ResponseEntity<PageResponse<RackInfoDto>> response = loadBalancedRestTemplate.exchange(
                    BASE + "/warehouse/" + warehouseId + "?size=100",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );
            PageResponse<RackInfoDto> body = response.getBody();
            return body != null ? body.contentOrEmpty() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch racks for warehouse {}: {}", warehouseId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<CellInfoDto> getCellsByRack(UUID rackId, String userRole) {
        try {
            HttpHeaders headers = buildHeaders(userRole);
            ResponseEntity<List<Map<String, Object>>> response = loadBalancedRestTemplate.exchange(
                    BASE + "/" + rackId + "/cells",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );
            List<Map<String, Object>> body = response.getBody();
            if (body == null) return Collections.emptyList();
            return body.stream().map(m -> new CellInfoDto(
                    parseUuid(m.get("cellId")),
                    rackId,
                    parseDecimal(m.get("maxWeightKg")),
                    parseDecimal(m.get("lengthCm")),
                    parseDecimal(m.get("widthCm")),
                    parseDecimal(m.get("heightCm"))
            )).toList();
        } catch (Exception e) {
            log.error("Failed to fetch cells for rack {}: {}", rackId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public Map<String, Object> getCellInfo(UUID cellId, String userRole) {
        try {
            HttpHeaders headers = buildHeaders(userRole);
            ResponseEntity<Map<String, Object>> response = loadBalancedRestTemplate.exchange(
                    BASE + "/cells/" + cellId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch cell info {}: {}", cellId, e.getMessage());
            return null;
        }
    }

    public RackInfoDto getRack(UUID rackId, String userRole) {
        try {
            HttpHeaders headers = buildHeaders(userRole);
            ResponseEntity<RackInfoDto> response = loadBalancedRestTemplate.exchange(
                    BASE + "/" + rackId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    RackInfoDto.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch rack {}: {}", rackId, e.getMessage());
            return null;
        }
    }

    private HttpHeaders buildHeaders(String userRole) {
        HttpHeaders headers = new HttpHeaders();
        if (userRole != null) {
            headers.set("X-User-Role", userRole);
        }
        return headers;
    }

    private UUID parseUuid(Object value) {
        if (value == null) return null;
        return UUID.fromString(value.toString());
    }

    private java.math.BigDecimal parseDecimal(Object value) {
        if (value == null) return null;
        return new java.math.BigDecimal(value.toString());
    }
}
