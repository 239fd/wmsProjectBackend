package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.CreateBatchRequest;
import by.bsuir.productservice.dto.response.BatchResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.ProductBatch;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.repository.ProductBatchRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchService Tests")
class BatchServiceTest {

    @Mock
    private ProductBatchRepository batchRepository;

    @Mock
    private ProductReadModelRepository productRepository;

    @InjectMocks
    private BatchService batchService;

    private UUID productId;
    private UUID batchId;
    private ProductReadModel product;
    private CreateBatchRequest createBatchRequest;
    private ProductBatch productBatch;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        batchId = UUID.randomUUID();

        product = ProductReadModel.builder()
                .productId(productId)
                .name("Test Product")
                .sku("SKU-001")
                .build();

        createBatchRequest = new CreateBatchRequest(
                productId,
                "BATCH-001",
                LocalDate.now(),
                LocalDate.now().plusMonths(6),
                "Test Supplier",
                new BigDecimal("100.00")
        );

        productBatch = ProductBatch.builder()
                .batchId(batchId)
                .productId(productId)
                .batchNumber("BATCH-001")
                .manufactureDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusMonths(6))
                .supplier("Test Supplier")
                .purchasePrice(new BigDecimal("100.00"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should create batch successfully")
    void shouldCreateBatchSuccessfully() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(batchRepository.save(any(ProductBatch.class))).thenReturn(productBatch);

        BatchResponse response = batchService.createBatch(createBatchRequest);

        assertThat(response).isNotNull();
        assertThat(response.productId()).isEqualTo(productId);
        assertThat(response.batchNumber()).isEqualTo("BATCH-001");
        verify(productRepository, times(1)).findById(productId);
        verify(batchRepository, times(1)).save(any(ProductBatch.class));
    }

    @Test
    @DisplayName("Should throw exception when product not found")
    void shouldThrowExceptionWhenProductNotFound() {
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> batchService.createBatch(createBatchRequest))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найден");

        verify(batchRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should get batches by product")
    void shouldGetBatchesByProduct() {
        List<ProductBatch> batches = Arrays.asList(productBatch);
        when(batchRepository.findByProductIdOrderByCreatedAtDesc(productId)).thenReturn(batches);

        List<BatchResponse> responses = batchService.getBatchesByProduct(productId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).productId()).isEqualTo(productId);
        verify(batchRepository, times(1)).findByProductIdOrderByCreatedAtDesc(productId);
    }

    @Test
    @DisplayName("Should get batch by ID")
    void shouldGetBatchById() {
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(productBatch));

        BatchResponse response = batchService.getBatch(batchId);

        assertThat(response).isNotNull();
        assertThat(response.batchId()).isEqualTo(batchId);
        verify(batchRepository, times(1)).findById(batchId);
    }

    @Test
    @DisplayName("Should throw exception when batch not found")
    void shouldThrowExceptionWhenBatchNotFound() {
        when(batchRepository.findById(batchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> batchService.getBatch(batchId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найдена");

        verify(batchRepository, times(1)).findById(batchId);
    }

    @Test
    @DisplayName("Should get all batches")
    void shouldGetAllBatches() {
        List<ProductBatch> batches = Arrays.asList(productBatch);
        when(batchRepository.findAll()).thenReturn(batches);

        List<BatchResponse> responses = batchService.getAllBatches();

        assertThat(responses).hasSize(1);
        verify(batchRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Should handle empty batches list")
    void shouldHandleEmptyBatchesList() {
        when(batchRepository.findByProductIdOrderByCreatedAtDesc(productId))
                .thenReturn(Collections.emptyList());

        List<BatchResponse> responses = batchService.getBatchesByProduct(productId);

        assertThat(responses).isEmpty();
        verify(batchRepository, times(1)).findByProductIdOrderByCreatedAtDesc(productId);
    }
}

