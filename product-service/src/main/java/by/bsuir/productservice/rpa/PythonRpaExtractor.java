package by.bsuir.productservice.rpa;

import by.bsuir.productservice.config.RpaProperties;
import by.bsuir.productservice.dto.import_.SupplyDto;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("oneCExtractor")
@ConditionalOnProperty(prefix = "rpa.python", name = "enabled", havingValue = "true")
public class PythonRpaExtractor implements SupplyExtractor {

    private static final String SOURCE_NAME = "1C-Python";

    private final RestClient restClient;
    private final ObjectMapper supplyMapper;

    public PythonRpaExtractor(RpaProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int millis = (int) Duration.ofSeconds(props.getPython().getTimeoutSeconds()).toMillis();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(millis);
        this.restClient = RestClient.builder()
                .baseUrl(props.getPython().getBaseUrl())
                .requestFactory(factory)
                .build();
        this.supplyMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<SupplyDto> extractSupplies() {
        log.info("PythonRpaExtractor: POST /parse/supplies");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/parse/supplies")
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                log.warn("PythonRpaExtractor: пустой ответ от rpa-service");
                return Collections.emptyList();
            }
            return convert(response);
        } catch (RestClientResponseException e) {
            log.error("PythonRpaExtractor: HTTP {} от rpa-service: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException(
                    "Python RPA /parse/supplies HTTP " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("PythonRpaExtractor: rpa-service недоступен: {}", e.getMessage(), e);
            throw new IllegalStateException("Python RPA unreachable: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<SupplyDto> convert(Map<String, Object> response) {
        Object suppliesObj = response.get("supplies");
        if (!(suppliesObj instanceof List<?> supplies)) {
            log.warn("PythonRpaExtractor: в ответе нет 'supplies' (keys={})", response.keySet());
            return Collections.emptyList();
        }
        List<SupplyDto> result = new ArrayList<>();
        for (Object raw : supplies) {
            if (!(raw instanceof Map<?, ?> supplyMap)) continue;
            Map<String, Object> normalized = unwrapSupply((Map<String, Object>) supplyMap);
            try {
                SupplyDto dto = supplyMapper.convertValue(normalized, SupplyDto.class);
                Map<String, Object> snapshot = buildSnapshot(normalized);
                int itemsCount = dto.items() != null ? dto.items().size() : 0;
                if (dto.totalItems() != null && dto.totalItems() > itemsCount) {
                    itemsCount = dto.totalItems();
                }
                result.add(new SupplyDto(
                        dto.externalId(),
                        dto.supplier(),
                        dto.warehouseId(),
                        dto.expectedDate(),
                        dto.currency(),
                        dto.totalAmount(),
                        dto.notes(),
                        Boolean.TRUE,
                        itemsCount,
                        null,
                        snapshot));
            } catch (Exception ex) {
                log.warn("PythonRpaExtractor: не удалось распарсить supply: {}", ex.getMessage());
            }
        }
        log.info("PythonRpaExtractor: получено {} supply", result.size());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapSupply(Map<String, Object> raw) {
        Object nested = raw.get("supply");
        if (nested instanceof Map<?, ?>) {
            return (Map<String, Object>) nested;
        }
        return raw;
    }

    private Map<String, Object> buildSnapshot(Map<String, Object> supplyMap) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (String key : List.of("transport", "commission", "international",
                "receipt_session", "inventory_session", "discrepancies",
                "writeoff", "revaluation", "generated_documents",
                "extraction_log", "operation")) {
            Object value = supplyMap.get(key);
            if (value != null) snapshot.put(key, value);
        }
        return snapshot.isEmpty() ? new HashMap<>() : snapshot;
    }
}
