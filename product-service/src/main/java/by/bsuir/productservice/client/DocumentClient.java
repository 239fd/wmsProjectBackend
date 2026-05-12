package by.bsuir.productservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentClient {

    private static final String BASE = "http://DOCUMENT-SERVICE/api/documents";

    private final RestTemplate loadBalancedRestTemplate;

    public UUID generateReceiptOrder(Map<String, Object> payload, UUID organizationId) {
        return generate("receipt-order", payload, organizationId);
    }

    public UUID generateWriteOffAct(Map<String, Object> payload, UUID organizationId) {
        return generate("write-off-act", payload, organizationId);
    }

    public UUID generateRevaluationAct(Map<String, Object> payload, UUID organizationId) {
        return generate("revaluation-act", payload, organizationId);
    }

    private UUID generate(String type, Map<String, Object> payload, UUID organizationId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (organizationId != null) {
                headers.set("X-Organization-Id", organizationId.toString());
            }
            ResponseEntity<Map<String, Object>> response = loadBalancedRestTemplate.exchange(
                    BASE + "/" + type + "?format=pdf",
                    org.springframework.http.HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    new org.springframework.core.ParameterizedTypeReference<>() {}
            );
            Map<String, Object> body = response.getBody();
            if (body == null || body.get("documentId") == null) {
                log.warn("document-service вернул пустое тело для {}", type);
                return null;
            }
            return UUID.fromString(body.get("documentId").toString());
        } catch (Exception e) {
            log.warn("Не удалось сгенерировать документ {} в document-service: {}", type, e.getMessage());
            return null;
        }
    }
}
