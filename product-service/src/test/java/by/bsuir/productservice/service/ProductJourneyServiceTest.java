package by.bsuir.productservice.service;

import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductBatch;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.enums.OperationType;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductBatchRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductJourneyService — модульные тесты")
class ProductJourneyServiceTest {

    @Mock private ProductReadModelRepository productRepository;
    @Mock private ProductBatchRepository batchRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductOperationRepository operationRepository;

    @InjectMocks private ProductJourneyService service;

    private ProductReadModel product(UUID id, UUID orgId, String abc) {
        return ProductReadModel.builder()
                .productId(id).organizationId(orgId)
                .name("Test").sku("SKU-1").category("food")
                .abcClass(abc).price(BigDecimal.TEN).build();
    }

    @Test
    @DisplayName("getJourney: товар не найден → notFound")
    void getJourney_GivenMissingProduct_ShouldThrow() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJourney(id, null, null, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найден");
    }

    @Test
    @DisplayName("getJourney: чужая организация → forbidden")
    void getJourney_GivenForeignOrg_ShouldThrow() {
        UUID id = UUID.randomUUID();
        UUID productOrg = UUID.randomUUID();
        UUID callerOrg = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.of(product(id, productOrg, "A")));

        assertThatThrownBy(() -> service.getJourney(id, null, null, callerOrg))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("другой организации");
    }

    @Test
    @DisplayName("getJourney: без batchId/inventoryId/orgId → возвращает product+batches+stocks+operations")
    void getJourney_ShouldAggregateAll() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product(productId, null, "B")));
        when(batchRepository.findByProductId(productId)).thenReturn(List.of(
                ProductBatch.builder().batchId(UUID.randomUUID()).batchNumber("B-1").build()));
        when(inventoryRepository.findByProductId(productId)).thenReturn(List.of(
                Inventory.builder().inventoryId(UUID.randomUUID()).quantity(BigDecimal.TEN).build()));
        when(operationRepository.findByProductIdOrderByOperationDateDesc(productId)).thenReturn(List.of(
                ProductOperation.builder().operationId(UUID.randomUUID())
                        .operationType(OperationType.RECEIPT).quantity(BigDecimal.TEN)
                        .operationDate(LocalDateTime.now()).build()));

        Map<String, Object> result = service.getJourney(productId, null, null, null);

        assertThat(result).containsKeys("productId", "productName", "sku", "category", "abcClass",
                "batches", "currentStocks", "operations");
        assertThat((List<?>) result.get("batches")).hasSize(1);
        assertThat((List<?>) result.get("currentStocks")).hasSize(1);
        assertThat((List<?>) result.get("operations")).hasSize(1);
    }

    @Test
    @DisplayName("getJourney: с orgId → дёргает org-scoped репозиторий, фильтрует stocks/operations")
    void getJourney_GivenOrgId_ShouldUseScopedRepos() {
        UUID productId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product(productId, orgId, "A")));
        when(batchRepository.findByOrganizationIdAndProductId(orgId, productId)).thenReturn(List.of());
        when(inventoryRepository.findByOrganizationIdAndProductId(orgId, productId)).thenReturn(List.of());
        when(operationRepository.findByOrganizationIdAndProductIdOrderByOperationDateDesc(orgId, productId))
                .thenReturn(List.of());

        Map<String, Object> result = service.getJourney(productId, null, null, orgId);

        assertThat(result.get("productId")).isEqualTo(productId);
    }

    @Test
    @DisplayName("getJourney: с batchId → фильтрует stocks/operations по batchId")
    void getJourney_GivenBatchId_ShouldFilter() {
        UUID productId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        UUID otherBatchId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product(productId, null, "B")));
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(
                ProductBatch.builder().batchId(batchId).build()));
        when(inventoryRepository.findByProductId(productId)).thenReturn(List.of(
                Inventory.builder().inventoryId(UUID.randomUUID()).batchId(batchId).build(),
                Inventory.builder().inventoryId(UUID.randomUUID()).batchId(otherBatchId).build()));
        when(operationRepository.findByProductIdOrderByOperationDateDesc(productId)).thenReturn(List.of(
                ProductOperation.builder().operationId(UUID.randomUUID()).batchId(batchId).operationType(OperationType.RECEIPT).operationDate(LocalDateTime.now()).build(),
                ProductOperation.builder().operationId(UUID.randomUUID()).batchId(otherBatchId).operationType(OperationType.RECEIPT).operationDate(LocalDateTime.now()).build()));

        Map<String, Object> result = service.getJourney(productId, batchId, null, null);

        assertThat((List<?>) result.get("currentStocks")).hasSize(1);
        assertThat((List<?>) result.get("operations")).hasSize(1);
    }

    @Test
    @DisplayName("getJourney: c inventoryId → ограничивает stocks одной записью")
    void getJourney_GivenInventoryId_ShouldReturnSingleStock() {
        UUID productId = UUID.randomUUID();
        UUID invId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product(productId, null, "B")));
        when(batchRepository.findByProductId(productId)).thenReturn(List.of());
        when(inventoryRepository.findById(invId)).thenReturn(Optional.of(
                Inventory.builder().inventoryId(invId).quantity(BigDecimal.ONE).build()));
        when(operationRepository.findByProductIdOrderByOperationDateDesc(productId)).thenReturn(List.of());

        Map<String, Object> result = service.getJourney(productId, null, invId, null);

        assertThat((List<?>) result.get("currentStocks")).hasSize(1);
    }

    @Test
    @DisplayName("generateJourneyPdf: возвращает валидный PDF")
    void generateJourneyPdf_GivenValidProduct_ShouldReturnPdf() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product(productId, null, "A")));
        when(batchRepository.findByProductId(productId)).thenReturn(List.of());
        when(inventoryRepository.findByProductId(productId)).thenReturn(List.of());
        when(operationRepository.findByProductIdOrderByOperationDateDesc(productId)).thenReturn(List.of(
                ProductOperation.builder().operationId(UUID.randomUUID())
                        .operationType(OperationType.RECEIPT).quantity(BigDecimal.TEN)
                        .operationDate(LocalDateTime.now()).warehouseId(UUID.randomUUID()).build()));

        byte[] pdf = service.generateJourneyPdf(productId, null, null, null);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
