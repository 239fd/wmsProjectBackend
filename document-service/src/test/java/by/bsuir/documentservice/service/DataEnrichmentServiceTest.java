package by.bsuir.documentservice.service;

import by.bsuir.documentservice.client.OrganizationClient;
import by.bsuir.documentservice.client.ProductClient;
import by.bsuir.documentservice.client.WarehouseClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataEnrichmentService — модульные тесты")
class DataEnrichmentServiceTest {

    @Mock private ProductClient productClient;
    @Mock private WarehouseClient warehouseClient;
    @Mock private OrganizationClient organizationClient;

    @InjectMocks private DataEnrichmentService service;

    @Test
    @DisplayName("enrich: продукт уже подставлен (productName != null) → не дёргает productClient")
    void enrich_GivenProductNameAlreadyPresent_ShouldSkipProductClient() {
        UUID productId = UUID.randomUUID();
        Map<String, Object> data = new HashMap<>(Map.of(
                "productId", productId.toString(),
                "productName", "Already set"
        ));

        Map<String, Object> result = service.enrich(data, UUID.randomUUID());

        verify(productClient, never()).getProduct(productId, null);
        assertThat(result.get("productName")).isEqualTo("Already set");
    }

    @Test
    @DisplayName("enrich: подставляет productName/sku/unitPrice из продуктового client'а")
    void enrich_GivenMissingProduct_ShouldFetchAndFill() {
        UUID productId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        when(productClient.getProduct(productId, orgId)).thenReturn(Map.of(
                "name", "Хлеб",
                "sku", "BREAD-1",
                "price", "2.50"
        ));

        Map<String, Object> data = new HashMap<>(Map.of("productId", productId.toString()));
        Map<String, Object> result = service.enrich(data, orgId);

        assertThat(result.get("productName")).isEqualTo("Хлеб");
        assertThat(result.get("sku")).isEqualTo("BREAD-1");
        assertThat(result.get("unitPrice")).isEqualTo("2.50");
    }

    @Test
    @DisplayName("enrich: невалидный UUID-параметр → пропускается без падения")
    void enrich_GivenInvalidUuid_ShouldSkip() {
        Map<String, Object> data = new HashMap<>(Map.of("productId", "not-a-uuid"));
        Map<String, Object> result = service.enrich(data, null);

        verify(productClient, never()).getProduct(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(result).doesNotContainKey("productName");
    }

    @Test
    @DisplayName("enrich: подставляет batchNumber/expiryDate/storageConditions из партии")
    void enrich_GivenMissingBatch_ShouldFetchAndFill() {
        UUID batchId = UUID.randomUUID();
        when(productClient.getBatch(batchId, null)).thenReturn(Map.of(
                "batchNumber", "BATCH-001",
                "expiryDate", "2027-01-01",
                "storageConditions", "FRIDGE"
        ));

        Map<String, Object> data = new HashMap<>(Map.of("batchId", batchId.toString()));
        Map<String, Object> result = service.enrich(data, null);

        assertThat(result.get("batchNumber")).isEqualTo("BATCH-001");
        assertThat(result.get("batchExpiry")).isEqualTo("2027-01-01");
        assertThat(result.get("storageConditions")).isEqualTo("FRIDGE");
    }

    @Test
    @DisplayName("enrich: подставляет warehouseName/warehouseAddress из warehouse client'а")
    void enrich_GivenMissingWarehouse_ShouldFetchAndFill() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseClient.getWarehouse(warehouseId)).thenReturn(Map.of(
                "name", "Главный склад",
                "address", "Минск, ул. Тест 1"
        ));

        Map<String, Object> data = new HashMap<>(Map.of("warehouseId", warehouseId.toString()));
        Map<String, Object> result = service.enrich(data, null);

        assertThat(result.get("warehouseName")).isEqualTo("Главный склад");
        assertThat(result.get("warehouseAddress")).isEqualTo("Минск, ул. Тест 1");
    }

    @Test
    @DisplayName("enrich: подставляет organizationName/inn/unp")
    void enrich_GivenOrganizationId_ShouldFetchOrganization() {
        UUID orgId = UUID.randomUUID();
        when(organizationClient.getOrganization(orgId)).thenReturn(Map.of(
                "name", "ОАО Тест",
                "inn", "123456789",
                "unp", "123456789"
        ));

        Map<String, Object> data = new HashMap<>();
        Map<String, Object> result = service.enrich(data, orgId);

        assertThat(result.get("organizationName")).isEqualTo("ОАО Тест");
        assertThat(result.get("inn")).isEqualTo("123456789");
        assertThat(result.get("unp")).isEqualTo("123456789");
    }

    @Test
    @DisplayName("enrich: null от client'а → не падает, поля не добавляются")
    void enrich_GivenClientReturnsNull_ShouldSkipGracefully() {
        UUID productId = UUID.randomUUID();
        when(productClient.getProduct(productId, null)).thenReturn(null);

        Map<String, Object> data = new HashMap<>(Map.of("productId", productId.toString()));
        Map<String, Object> result = service.enrich(data, null);

        assertThat(result).doesNotContainKey("productName");
    }

    @Test
    @DisplayName("enrich: возвращает новую Map, исходную не мутирует")
    void enrich_ShouldNotMutateOriginal() {
        Map<String, Object> data = new HashMap<>();
        data.put("foo", "bar");
        Map<String, Object> result = service.enrich(data, null);

        assertThat(result).isNotSameAs(data);
        assertThat(result.get("foo")).isEqualTo("bar");
        assertThat(data).hasSize(1);
    }
}
