package by.bsuir.productservice.rpa;

import by.bsuir.productservice.config.RpaProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component("oneCExtractor")
@ConditionalOnProperty(prefix = "rpa.python", name = "enabled", havingValue = "true")
public class PythonRpaExtractor implements PlannedDeliveryExtractor {

    private static final String SOURCE_NAME = "1C-Python";

    private final RpaProperties props;
    private final RestClient restClient;

    public PythonRpaExtractor(RpaProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int millis = (int) Duration.ofSeconds(props.getPython().getTimeoutSeconds()).toMillis();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(millis);
        this.restClient = RestClient.builder()
                .baseUrl(props.getPython().getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<Map<String, Object>> extractDeliveries() {
        log.info("PythonRpaExtractor: POST {}/parse/supplies", props.getPython().getBaseUrl());
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
            return flatten(response);
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

    private List<Map<String, Object>> flatten(Map<String, Object> response) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Object suppliesObj = response.get("supplies");
        if (!(suppliesObj instanceof List<?> supplies)) {
            log.warn("PythonRpaExtractor: в ответе нет поля 'supplies' (got keys={})", response.keySet());
            return rows;
        }
        for (Object supplyObj : supplies) {
            if (!(supplyObj instanceof Map<?, ?> supplyMap)) {
                continue;
            }
            String supplyExternalId = asString(supplyMap.get("external_id"));
            String expectedDate = asString(supplyMap.get("expected_date"));
            String supplierName = "";
            Object supplierObj = supplyMap.get("supplier");
            if (supplierObj instanceof Map<?, ?> supplierMap) {
                supplierName = asString(supplierMap.get("name"));
            }

            Object itemsObj = supplyMap.get("supply_items");
            if (!(itemsObj instanceof List<?> items) || items.isEmpty()) {
                continue;
            }
            for (Object itemObj : items) {
                if (!(itemObj instanceof Map<?, ?> itemMap)) {
                    continue;
                }
                String productName = "";
                String sku = "";
                Object productObj = itemMap.get("product");
                if (productObj instanceof Map<?, ?> productMap) {
                    productName = asString(productMap.get("name"));
                    sku = asString(productMap.get("sku"));
                }
                int qty = parseQuantity(itemMap.get("expected_qty"));
                String rowKey = !sku.isBlank() ? sku : asString(itemMap.get("row_number"));
                String rowExternalId = supplyExternalId.isBlank() && rowKey.isBlank()
                        ? null
                        : supplyExternalId + "#" + rowKey;

                Map<String, Object> row = new HashMap<>();
                row.put("externalId", rowExternalId);
                row.put("supplierName", supplierName);
                row.put("productName", productName);
                row.put("expectedQuantity", qty);
                row.put("expectedDate", expectedDate);
                rows.add(row);
            }
        }
        log.info("PythonRpaExtractor: flattened {} item-row(s) из {} supply(es)",
                rows.size(), supplies.size());
        return rows;
    }

    private static String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private static int parseQuantity(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            String s = value.toString().trim();
            if (s.isEmpty()) {
                return 0;
            }
            return (int) Math.floor(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
