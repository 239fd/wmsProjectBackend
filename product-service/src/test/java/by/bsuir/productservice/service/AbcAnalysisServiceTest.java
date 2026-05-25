package by.bsuir.productservice.service;

import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.enums.OperationType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AbcAnalysisService Tests")
class AbcAnalysisServiceTest {

    @Mock
    private ProductOperationRepository operationRepository;

    @Mock
    private ProductReadModelRepository productRepository;

    @InjectMocks
    private AbcAnalysisService service;

    private ProductOperation op(UUID productId, BigDecimal qty, OperationType type) {
        return ProductOperation.builder()
                .operationId(UUID.randomUUID())
                .productId(productId)
                .operationType(type)
                .quantity(qty)
                .operationDate(LocalDateTime.now())
                .build();
    }

    private ProductReadModel product(UUID id, String name) {
        return ProductReadModel.builder()
                .productId(id)
                .name(name)
                .sku("SKU-" + name)
                .build();
    }

    @Test
    @DisplayName("runManually: распределяет товары на A/B/C по 80/15/5 правилу")
    void runManually_givenSkewedTurnover_whenCalled_thenAssignsClasses() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();
        when(operationRepository.findByOperationDateBetween(any(), any()))
                .thenReturn(List.of(
                        op(p1, new BigDecimal("800"), OperationType.SHIPMENT),
                        op(p2, new BigDecimal("150"), OperationType.SHIPMENT),
                        op(p3, new BigDecimal("50"), OperationType.WRITE_OFF)));
        when(productRepository.findById(p1)).thenReturn(Optional.of(product(p1, "p1")));
        when(productRepository.findById(p2)).thenReturn(Optional.of(product(p2, "p2")));
        when(productRepository.findById(p3)).thenReturn(Optional.of(product(p3, "p3")));
        when(productRepository.save(any(ProductReadModel.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.runManually();

        assertThat(result.get("class_a_count")).isEqualTo(1L);
        assertThat(result.get("class_b_count")).isEqualTo(1L);
        assertThat(result.get("class_c_count")).isEqualTo(1L);
        assertThat(result.get("period_days")).isEqualTo(90);
    }

    @Test
    @DisplayName("runManually: нет SHIPMENT/WRITE_OFF операций → пустой результат")
    void runManually_givenOnlyReceives_whenCalled_thenReturnsEmpty() {
        UUID p1 = UUID.randomUUID();
        when(operationRepository.findByOperationDateBetween(any(), any()))
                .thenReturn(List.of(op(p1, new BigDecimal("100"), OperationType.RECEIPT)));

        Map<String, Object> result = service.runManually();

        assertThat(result.get("class_a_count")).isEqualTo(0L);
        assertThat(result.get("class_b_count")).isEqualTo(0L);
        assertThat(result.get("class_c_count")).isEqualTo(0L);
        verify(productRepository, never()).save(any(ProductReadModel.class));
    }

    @Test
    @DisplayName("runManually: вообще нет операций → не сохраняет classes")
    void runManually_givenNoOperations_whenCalled_thenReturnsEmpty() {
        when(operationRepository.findByOperationDateBetween(any(), any()))
                .thenReturn(new ArrayList<>());

        Map<String, Object> result = service.runManually();

        assertThat(result.get("class_a_count")).isEqualTo(0L);
        verify(productRepository, never()).save(any(ProductReadModel.class));
    }

    @Test
    @DisplayName("runManually: продукт удалён между расчётом и сохранением — не падает")
    void runManually_givenMissingProduct_whenCalled_thenSkipsGracefully() {
        UUID p1 = UUID.randomUUID();
        when(operationRepository.findByOperationDateBetween(any(), any()))
                .thenReturn(List.of(op(p1, new BigDecimal("100"), OperationType.SHIPMENT)));
        when(productRepository.findById(p1)).thenReturn(Optional.empty());

        Map<String, Object> result = service.runManually();

        assertThat(result.get("class_c_count")).isEqualTo(1L);
        verify(productRepository, never()).save(any(ProductReadModel.class));
    }

    @Test
    @DisplayName("runDailyAbcAnalysis: вызывает calculateAndSave (cron-обёртка)")
    void runDailyAbcAnalysis_whenCalled_thenDelegatesToCalculate() {
        when(operationRepository.findByOperationDateBetween(any(), any()))
                .thenReturn(List.of());

        service.runDailyAbcAnalysis();

        verify(operationRepository).findByOperationDateBetween(any(), any());
    }

    @Test
    @DisplayName("getAbcReport: фильтрует только продукты с abcClass и сортирует A→B→C")
    void getAbcReport_givenProductsWithMixedClasses_whenCalled_thenFiltersAndSorts() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID none = UUID.randomUUID();

        ProductReadModel pa = product(a, "A-prod");
        pa.setAbcClass("A");
        ProductReadModel pb = product(b, "B-prod");
        pb.setAbcClass("B");
        ProductReadModel pn = product(none, "N-prod");
        when(productRepository.findAll()).thenReturn(List.of(pb, pa, pn));

        List<Map<String, Object>> report = service.getAbcReport();

        assertThat(report).hasSize(2);
        assertThat(report.get(0).get("abcClass")).isEqualTo("A");
        assertThat(report.get(1).get("abcClass")).isEqualTo("B");
        assertThat(report.get(0).get("productId")).isEqualTo(a);
    }
}
