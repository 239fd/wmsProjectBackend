package by.bsuir.productservice.rpa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("apiExtractor")
public class ApiExtractorImpl implements PlannedDeliveryExtractor {

    @Value("${erp.api.base-url:http://localhost:8040/mock-erp}")
    private String erpBaseUrl;

    @Value("${erp.api.username:admin}")
    private String erpUsername;

    @Value("${erp.api.password:admin}")
    private String erpPassword;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getSourceName() {
        return "API";
    }

    @Override
    public List<Map<String, Object>> extractDeliveries() {
        log.info("API-экстрактор: начало извлечения из {}", erpBaseUrl);

        try {
            String token = login();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    erpBaseUrl + "/api/deliveries",
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            List<Map<String, Object>> deliveries = response.getBody();
            log.info("API-экстрактор: извлечено {} записей", deliveries != null ? deliveries.size() : 0);
            return deliveries != null ? deliveries : Collections.emptyList();

        } catch (Exception e) {
            log.error("API-экстрактор: ошибка извлечения: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка API-экстрактора: " + e.getMessage(), e);
        }
    }

    private String login() {
        String url = erpBaseUrl + "/login";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", erpUsername);
        form.add("password", erpPassword);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
        Map<?, ?> response = restTemplate.postForObject(url, request, Map.class);
        if (response == null || !response.containsKey("token")) {
            throw new RuntimeException("Не удалось получить токен от ERP API");
        }
        return (String) response.get("token");
    }
}