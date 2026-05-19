package by.bsuir.productservice.client;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentClient {

    private static final String BASE = "http://DOCUMENT-SERVICE/api/documents";

    private final RestTemplate loadBalancedRestTemplate;

    public byte[] fetchPdf(String type, Map<String, Object> payload, UUID organizationId) {
        return fetch(type, payload, organizationId, "auto").body;
    }

    public Fetched fetch(String type, Map<String, Object> payload, UUID organizationId, String mode) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.ALL));
            if (organizationId != null) {
                headers.set("X-Organization-Id", organizationId.toString());
            }
            String effectiveMode = mode != null ? mode : "auto";
            headers.set("X-Generation-Mode", effectiveMode);

            ResponseEntity<byte[]> response = loadBalancedRestTemplate.exchange(
                    BASE + "/" + type + "?format=pdf",
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    byte[].class);
            String channel = response.getHeaders().getFirst("X-Generation-Channel");
            String contentType = response.getHeaders().getContentType() != null
                    ? response.getHeaders().getContentType().toString()
                    : null;
            String filename = null;
            if (response.getHeaders().getContentDisposition() != null) {
                filename = response.getHeaders().getContentDisposition().getFilename();
            }
            return new Fetched(
                    response.getBody(),
                    channel != null ? channel : "programmatic",
                    contentType,
                    filename);
        } catch (Exception e) {
            log.error("Не удалось получить документ {} из document-service (org={}, mode={}): {}",
                    type, organizationId, mode, e.getMessage(), e);
            return new Fetched(null, "error", null, null);
        }
    }

    public record Fetched(byte[] body, String channel, String contentType, String filename) { }
}
