package by.bsuir.productservice.rpa;

import by.bsuir.productservice.config.RpaProperties;
import by.bsuir.productservice.dto.import_.SalesOrderDto;
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
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "rpa.python", name = "enabled", havingValue = "true")
public class PythonRpaSalesExtractor {

    private final RestClient restClient;
    private final ObjectMapper salesMapper;

    public PythonRpaSalesExtractor(RpaProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int millis = (int) Duration.ofSeconds(props.getPython().getTimeoutSeconds()).toMillis();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(millis);
        this.restClient = RestClient.builder()
                .baseUrl(props.getPython().getBaseUrl())
                .requestFactory(factory)
                .build();
        this.salesMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public List<SalesOrderDto> extractSales() {
        log.info("PythonRpaSalesExtractor: POST /parse/sales");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/parse/sales")
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                log.warn("PythonRpaSalesExtractor: пустой ответ от rpa-service");
                return Collections.emptyList();
            }
            return convert(response);
        } catch (RestClientResponseException e) {
            log.error("PythonRpaSalesExtractor: HTTP {} от rpa-service: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException(
                    "Python RPA /parse/sales HTTP " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("PythonRpaSalesExtractor: rpa-service недоступен: {}", e.getMessage(), e);
            throw new IllegalStateException("Python RPA unreachable: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<SalesOrderDto> convert(Map<String, Object> response) {
        Object shipmentsObj = response.get("shipments");
        if (!(shipmentsObj instanceof List<?> shipments)) {
            log.warn("PythonRpaSalesExtractor: в ответе нет 'shipments' (keys={})", response.keySet());
            return Collections.emptyList();
        }
        List<SalesOrderDto> result = new ArrayList<>();
        for (Object raw : shipments) {
            if (!(raw instanceof Map<?, ?> shipmentMap)) continue;
            try {
                SalesOrderDto dto = salesMapper.convertValue(shipmentMap, SalesOrderDto.class);
                result.add(dto);
            } catch (Exception ex) {
                log.warn("PythonRpaSalesExtractor: не удалось распарсить shipment: {}", ex.getMessage());
            }
        }
        log.info("PythonRpaSalesExtractor: получено {} shipment(s)", result.size());
        return result;
    }
}
