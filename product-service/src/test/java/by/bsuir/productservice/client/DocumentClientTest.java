package by.bsuir.productservice.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentClient — модульные тесты")
class DocumentClientTest {

    @Mock private RestTemplate loadBalancedRestTemplate;
    @InjectMocks private DocumentClient client;

    @Test
    @DisplayName("fetch: успешный ответ → возвращает body + X-Organization-Id header")
    void fetch_GivenSuccess_ShouldReturnBodyAndSendOrgHeader() {
        UUID orgId = UUID.randomUUID();
        byte[] pdf = "%PDF-1.4 fake".getBytes();
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add("X-Generation-Channel", "auto");

        when(loadBalancedRestTemplate.exchange(
                contains("/receipt-order?format=pdf"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(byte[].class))
        ).thenReturn(new ResponseEntity<>(pdf, responseHeaders, org.springframework.http.HttpStatus.OK));

        DocumentClient.Fetched result = client.fetch("receipt-order", Map.of("foo", "bar"), orgId, "auto");

        assertThat(result.body()).isEqualTo(pdf);
        assertThat(result.channel()).isEqualTo("auto");

        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalancedRestTemplate).exchange(
                contains("/receipt-order"),
                eq(HttpMethod.POST), captor.capture(), eq(byte[].class));
        assertThat(captor.getValue().getHeaders().getFirst("X-Organization-Id"))
                .isEqualTo(orgId.toString());
        assertThat(captor.getValue().getHeaders().getFirst("X-Generation-Mode"))
                .isEqualTo("auto");
    }

    @Test
    @DisplayName("fetchPdf: успешный ответ → возвращает только byte[] body")
    void fetchPdf_GivenSuccess_ShouldReturnBody() {
        byte[] pdf = "%PDF-1.4 fake".getBytes();
        when(loadBalancedRestTemplate.exchange(
                any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class))
        ).thenReturn(ResponseEntity.ok(pdf));

        byte[] result = client.fetchPdf("write-off-act", Map.of(), UUID.randomUUID());

        assertThat(result).isEqualTo(pdf);
    }

    @Test
    @DisplayName("fetch: document-service недоступен (исключение) → Fetched(null,\"error\") без проброса")
    void fetch_WhenDocumentServiceDown_ShouldReturnErrorChannelGracefully() {
        when(loadBalancedRestTemplate.exchange(
                any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class))
        ).thenThrow(new RuntimeException("connection refused"));

        DocumentClient.Fetched result = client.fetch("receipt-order", Map.of(), UUID.randomUUID(), "auto");

        assertThat(result.body()).isNull();
        assertThat(result.channel()).isEqualTo("error");
    }

    @Test
    @DisplayName("fetch: organizationId == null → X-Organization-Id не выставляется")
    void fetch_GivenNullOrgId_ShouldOmitHeader() {
        when(loadBalancedRestTemplate.exchange(
                any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class))
        ).thenReturn(ResponseEntity.ok(new byte[]{}));

        client.fetch("receipt-order", Map.of(), null, "auto");

        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalancedRestTemplate).exchange(
                any(String.class), eq(HttpMethod.POST), captor.capture(), eq(byte[].class));
        assertThat(captor.getValue().getHeaders().getFirst("X-Organization-Id")).isNull();
    }

    @Test
    @DisplayName("fetch: mode==null → дефолтный X-Generation-Mode = auto")
    void fetch_GivenNullMode_ShouldDefaultToAuto() {
        when(loadBalancedRestTemplate.exchange(
                any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class))
        ).thenReturn(ResponseEntity.ok(new byte[]{}));

        client.fetch("receipt-order", Map.of(), UUID.randomUUID(), null);

        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalancedRestTemplate).exchange(
                any(String.class), eq(HttpMethod.POST), captor.capture(), eq(byte[].class));
        assertThat(captor.getValue().getHeaders().getFirst("X-Generation-Mode")).isEqualTo("auto");
    }

    @Test
    @DisplayName("fetch: payload пробрасывается в HttpEntity body")
    void fetch_ShouldPassPayloadInRequestBody() {
        when(loadBalancedRestTemplate.exchange(
                any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class))
        ).thenReturn(ResponseEntity.ok(new byte[]{}));

        Map<String, Object> payload = new HashMap<>();
        payload.put("operationId", UUID.randomUUID().toString());
        payload.put("qty", 5);
        client.fetch("receipt-order", payload, UUID.randomUUID(), "auto");

        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalancedRestTemplate).exchange(
                any(String.class), eq(HttpMethod.POST), captor.capture(), eq(byte[].class));
        assertThat(captor.getValue().getBody()).isEqualTo(payload);
    }
}
