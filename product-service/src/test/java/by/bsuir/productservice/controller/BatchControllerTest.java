package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.request.CreateBatchRequest;
import by.bsuir.productservice.dto.response.BatchResponse;
import by.bsuir.productservice.service.BatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchController Unit Tests")
class BatchControllerTest {

    @Mock
    private BatchService batchService;

    @InjectMocks
    private BatchController batchController;

    @Test
    @DisplayName("createBatch: Given valid role Should create and return 201")
    void createBatch_GivenValidRole_ShouldCreateAndReturn201() {
        UUID productId = UUID.randomUUID();
        CreateBatchRequest request = new CreateBatchRequest(
                productId,
                "BATCH-001",
                LocalDate.now(),
                LocalDate.now().plusMonths(6),
                "Test Supplier",
                new BigDecimal("100.00")
        );

        BatchResponse expectedResponse = new BatchResponse(
                UUID.randomUUID(),
                productId,
                "BATCH-001",
                LocalDate.now(),
                LocalDate.now().plusMonths(6),
                "Test Supplier",
                new BigDecimal("100.00"),
                LocalDateTime.now()
        );

        when(batchService.createBatch(any(CreateBatchRequest.class))).thenReturn(expectedResponse);

        ResponseEntity<BatchResponse> response = batchController.createBatch(productId, request, "ADMIN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().batchNumber()).isEqualTo("BATCH-001");
        assertThat(response.getBody().productId()).isEqualTo(productId);
        verify(batchService, times(1)).createBatch(any(CreateBatchRequest.class));
    }

    @Test
    @DisplayName("createBatch: Given no role Should return 403")
    void createBatch_GivenNoRole_ShouldReturn403() {
        UUID productId = UUID.randomUUID();
        CreateBatchRequest request = new CreateBatchRequest(
                productId,
                "BATCH-001",
                LocalDate.now(),
                LocalDate.now().plusMonths(6),
                "Test Supplier",
                new BigDecimal("100.00")
        );

        ResponseEntity<BatchResponse> response = batchController.createBatch(productId, request, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(batchService, never()).createBatch(any(CreateBatchRequest.class));
    }

    @Test
    @DisplayName("getBatchesByProduct: Should return list of batches")
    void getBatchesByProduct_ShouldReturnListOfBatches() {
        UUID productId = UUID.randomUUID();
        BatchResponse batch1 = new BatchResponse(
                UUID.randomUUID(),
                productId,
                "BATCH-001",
                LocalDate.now(),
                LocalDate.now().plusMonths(6),
                "Supplier 1",
                new BigDecimal("100.00"),
                LocalDateTime.now()
        );

        when(batchService.getBatchesByProduct(productId)).thenReturn(List.of(batch1));

        ResponseEntity<List<BatchResponse>> response = batchController.getBatchesByProduct(productId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).batchNumber()).isEqualTo("BATCH-001");
        verify(batchService, times(1)).getBatchesByProduct(productId);
    }

    @Test
    @DisplayName("getBatch: Should return batch by ID")
    void getBatch_ShouldReturnBatchById() {
        UUID batchId = UUID.randomUUID();
        BatchResponse batch = new BatchResponse(
                batchId,
                UUID.randomUUID(),
                "BATCH-001",
                LocalDate.now(),
                LocalDate.now().plusMonths(6),
                "Test Supplier",
                new BigDecimal("100.00"),
                LocalDateTime.now()
        );

        when(batchService.getBatch(batchId)).thenReturn(batch);

        ResponseEntity<BatchResponse> response = batchController.getBatch(batchId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().batchId()).isEqualTo(batchId);
        verify(batchService, times(1)).getBatch(batchId);
    }

    @Test
    @DisplayName("getAllBatches: Should return all batches")
    void getAllBatches_ShouldReturnAllBatches() {
        BatchResponse batch1 = new BatchResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "BATCH-001",
                LocalDate.now(),
                LocalDate.now().plusMonths(6),
                "Supplier 1",
                new BigDecimal("100.00"),
                LocalDateTime.now()
        );

        when(batchService.getAllBatches()).thenReturn(List.of(batch1));

        ResponseEntity<List<BatchResponse>> response = batchController.getAllBatches();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).batchNumber()).isEqualTo("BATCH-001");
        verify(batchService, times(1)).getAllBatches();
    }
}

