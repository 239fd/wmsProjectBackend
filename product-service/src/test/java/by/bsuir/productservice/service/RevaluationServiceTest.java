package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.RevaluationRequest;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.enums.OperationType;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RevaluationService — модульные тесты")
class RevaluationServiceTest {

    @Mock private ProductReadModelRepository productRepository;
    @Mock private ProductOperationRepository operationRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryEventService inventoryEventService;

    @InjectMocks private RevaluationService service;

    @Test
    @DisplayName("revaluate: новая цена → обновляет продукт, операция REVALUATION с oldPrice/newPrice в notes")
    void revaluate_GivenNewPrice_ShouldUpdateAndRecordOperation() {
        UUID productId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        ProductReadModel product = ProductReadModel.builder()
                .productId(productId).organizationId(orgId)
                .price(BigDecimal.valueOf(100)).build();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        RevaluationRequest req = sampleReq(productId, warehouseId, userId, BigDecimal.valueOf(150));

        Map<String, Object> result = service.revaluate(req, orgId);

        assertThat(product.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(result).containsEntry("oldPrice", BigDecimal.valueOf(100));
        assertThat(result).containsEntry("newPrice", BigDecimal.valueOf(150));

        ArgumentCaptor<ProductOperation> opCaptor = ArgumentCaptor.forClass(ProductOperation.class);
        verify(operationRepository).save(opCaptor.capture());
        ProductOperation op = opCaptor.getValue();
        assertThat(op.getOperationType()).isEqualTo(OperationType.REVALUATION);
        assertThat(op.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(op.getOrganizationId()).isEqualTo(orgId);
        assertThat(op.getNotes()).contains("oldPrice=100");
        assertThat(op.getNotes()).contains("newPrice=150");
        assertThat(op.getNotes()).contains("reason=MARKET_CHANGE");
    }

    @Test
    @DisplayName("revaluate: товар не найден → 404")
    void revaluate_GivenMissingProduct_ShouldThrowNotFound() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.empty());
        RevaluationRequest req = sampleReq(productId, UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN);

        AppException ex = catchApp(() -> service.revaluate(req, null));
        assertThat(ex.getStatus().value()).isEqualTo(404);
        verify(operationRepository, never()).save(any());
    }

    @Test
    @DisplayName("revaluate: товар другой орг → 403 forbidden")
    void revaluate_GivenForeignTenant_ShouldThrowForbidden() {
        UUID productId = UUID.randomUUID();
        UUID myOrg = UUID.randomUUID();
        UUID otherOrg = UUID.randomUUID();
        ProductReadModel product = ProductReadModel.builder()
                .productId(productId).organizationId(otherOrg).price(BigDecimal.TEN).build();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        RevaluationRequest req = sampleReq(productId, UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(20));

        AppException ex = catchApp(() -> service.revaluate(req, myOrg));
        assertThat(ex.getStatus().value()).isEqualTo(403);
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("revaluate: новая цена равна текущей → 400 bad request")
    void revaluate_GivenSamePrice_ShouldThrowBadRequest() {
        UUID productId = UUID.randomUUID();
        ProductReadModel product = ProductReadModel.builder()
                .productId(productId).price(BigDecimal.valueOf(100)).build();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        RevaluationRequest req = sampleReq(productId, UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(100));

        AppException ex = catchApp(() -> service.revaluate(req, null));
        assertThat(ex.getStatus().value()).isEqualTo(400);
        assertThat(ex.getMessage()).contains("совпадает");
        verify(operationRepository, never()).save(any());
    }

    private RevaluationRequest sampleReq(UUID productId, UUID warehouseId, UUID userId, BigDecimal newPrice) {
        return new RevaluationRequest(
                productId, warehouseId, newPrice,
                "MARKET_CHANGE", "Приказ #1",
                UUID.randomUUID(), List.of(UUID.randomUUID()),
                userId, "коррекция");
    }

    private static AppException catchApp(Runnable r) {
        try {
            r.run();
        } catch (AppException e) {
            return e;
        }
        throw new AssertionError("Expected AppException");
    }
}
