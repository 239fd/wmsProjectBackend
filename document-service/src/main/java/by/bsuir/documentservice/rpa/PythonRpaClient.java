package by.bsuir.documentservice.rpa;

import by.bsuir.documentservice.config.RpaProperties;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * HTTP-клиент к Python rpa-service. Зовётся когда mode=rpa (заменил
 * удалённый OfficeDocumentBot на основе WinAppDriver).
 *
 * <p>Python-side обрабатывает payload как WMS Map&lt;String,Object&gt; и
 * возвращает bytes файла native-формата (.xlsx / .docx). Конверсии в PDF
 * на Python-side нет — клиент при необходимости использует фолбэк на
 * программный PDFBox-канал.
 */
@Slf4j
@Component
public class PythonRpaClient {

    private final RpaProperties props;
    private final RestClient restClient;

    public record FillResponse(byte[] body, String contentType, String filename) {}

    @Autowired
    public PythonRpaClient(RpaProperties props) {
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

    /** True если канал включён конфигом и health-эндпоинт отвечает. */
    public boolean isAvailable() {
        if (!props.getPython().isEnabled()) {
            return false;
        }
        try {
            restClient.get().uri("/health").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.debug("Python RPA health check failed: {}", e.getMessage());
            return false;
        }
    }

    /** Сгенерировать документ. Кидает {@link IllegalStateException} при сбое. */
    public FillResponse fill(String docType, Map<String, Object> payload) {
        if (!props.getPython().isEnabled()) {
            throw new IllegalStateException("Python RPA channel disabled (rpa.python.enabled=false)");
        }
        try {
            var response = restClient.post()
                    .uri("/fill/{type}", docType)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new IllegalStateException(
                                "Python RPA returned " + res.getStatusCode() + ": "
                                        + new String(res.getBody().readAllBytes()));
                    })
                    .toEntity(byte[].class);

            HttpHeaders headers = response.getHeaders();
            String filename = filenameFromHeaders(headers);
            String contentType = headers.getContentType() != null
                    ? headers.getContentType().toString()
                    : MediaType.APPLICATION_OCTET_STREAM_VALUE;
            byte[] body = response.getBody();
            log.info("Python RPA: {} bytes received for type={}, filename={}",
                    body != null ? body.length : 0, docType, filename);
            return new FillResponse(body, contentType, filename);
        } catch (RestClientResponseException e) {
            log.error("Python RPA call failed: type={}, status={}, body={}",
                    docType, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException(
                    "Python RPA HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Python RPA unreachable: type={}, error={}", docType, e.getMessage(), e);
            throw new IllegalStateException("Python RPA unreachable: " + e.getMessage(), e);
        }
    }

    private String filenameFromHeaders(HttpHeaders headers) {
        var cd = headers.getContentDisposition();
        if (cd != null && cd.getFilename() != null) {
            return cd.getFilename();
        }
        return null;
    }
}