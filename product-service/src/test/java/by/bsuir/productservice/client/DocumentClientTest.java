package by.bsuir.productservice.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
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
    @DisplayName("generateReceiptOrder: успешный ответ → возвращает documentId, шлёт X-Organization-Id")
    void generateReceiptOrder_GivenSuccess_ShouldReturnDocumentId() {
        UUID expectedId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Map<String, Object> body = new HashMap<>();
        body.put("documentId", expectedId.toString());

        when(loadBalancedRestTemplate.exchange(
                contains("/receipt-order?format=pdf"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class))
        ).thenReturn((ResponseEntity) ResponseEntity.ok(body));

        UUID id = client.generateReceiptOrder(Map.of("foo", "bar"), orgId);

        assertThat(id).isEqualTo(expectedId);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalancedRestTemplate).exchange(
                contains("/receipt-order"),
                eq(HttpMethod.POST), entityCaptor.capture(),
                any(ParameterizedTypeReference.class));
        assertThat(entityCaptor.getValue().getHeaders().getFirst("X-Organization-Id"))
                .isEqualTo(orgId.toString());
    }

    @Test
    @DisplayName("generateWriteOffAct: пустое тело ответа → возвращает null (graceful)")
    void generateWriteOffAct_GivenEmptyBody_ShouldReturnNull() {
        when(loadBalancedRestTemplate.exchange(
                contains("/write-off-act"),
                eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class))
        ).thenReturn((ResponseEntity) ResponseEntity.ok(null));

        UUID id = client.generateWriteOffAct(Map.of(), UUID.randomUUID());

        assertThat(id).isNull();
    }

    @Test
    @DisplayName("generateRevaluationAct: тело без documentId → null (graceful)")
    void generateRevaluationAct_GivenBodyWithoutDocumentId_ShouldReturnNull() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "ok");
        when(loadBalancedRestTemplate.exchange(
                contains("/revaluation-act"),
                eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class))
        ).thenReturn((ResponseEntity) ResponseEntity.ok(body));

        UUID id = client.generateRevaluationAct(Map.of(), UUID.randomUUID());

        assertThat(id).isNull();
    }

    @Test
    @DisplayName("generate*: document-service недоступен (исключение) → null без проброса исключения")
    void generate_WhenDocumentServiceDown_ShouldReturnNullGracefully() {
        when(loadBalancedRestTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class))
        ).thenThrow(new RuntimeException("connection refused"));

        UUID id = client.generateReceiptOrder(Map.of(), UUID.randomUUID());

        assertThat(id).isNull();
    }

    @Test
    @DisplayName("generate: organizationId == null → не выставляет X-Organization-Id")
    void generate_GivenNullOrgId_ShouldOmitHeader() {
        Map<String, Object> body = new HashMap<>();
        body.put("documentId", UUID.randomUUID().toString());
        when(loadBalancedRestTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class))
        ).thenReturn((ResponseEntity) ResponseEntity.ok(body));

        client.generateReceiptOrder(Map.of(), null);

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalancedRestTemplate).exchange(
                any(String.class), eq(HttpMethod.POST), entityCaptor.capture(),
                any(ParameterizedTypeReference.class));
        assertThat(entityCaptor.getValue().getHeaders().getFirst("X-Organization-Id")).isNull();
    }

    @Test
    @DisplayName("generate: payload пробрасывается в HttpEntity body")
    void generate_ShouldPassPayloadInRequestBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("documentId", UUID.randomUUID().toString());
        when(loadBalancedRestTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class))
        ).thenReturn((ResponseEntity) ResponseEntity.ok(body));

        Map<String, Object> payload = Map.of("operationId", UUID.randomUUID().toString(), "qty", 5);
        client.generateReceiptOrder(payload, UUID.randomUUID());

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalancedRestTemplate).exchange(
                any(String.class), eq(HttpMethod.POST), entityCaptor.capture(),
                any(ParameterizedTypeReference.class));
        assertThat(entityCaptor.getValue().getBody()).isEqualTo(payload);
    }
}
