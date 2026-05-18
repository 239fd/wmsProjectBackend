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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchController Tests")
class BatchControllerTest {

    @Mock
    private BatchService batchService;

    @InjectMocks
    private BatchController controller;

    private BatchResponse sample() {
        return new BatchResponse(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "BATCH-001", LocalDate.now(), LocalDate.now().plusMonths(6),
                "supplier", new BigDecimal("10"), null, LocalDateTime.now());
    }

    @Test
    @DisplayName("createBatch: WORKER role → 201 Created + service.createBatch")
    void createBatch_givenWorkerRole_whenCalled_thenReturns201() {
        UUID productId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        CreateBatchRequest req = new CreateBatchRequest(
                productId, "BATCH-001", LocalDate.now(), LocalDate.now().plusMonths(6),
                "s", new BigDecimal("10"), null, null);
        when(batchService.createBatch(any(CreateBatchRequest.class), eq(orgId))).thenReturn(sample());

        ResponseEntity<BatchResponse> response = controller.createBatch(productId, req, "WORKER", orgId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(batchService).createBatch(any(CreateBatchRequest.class), eq(orgId));
    }

    @Test
    @DisplayName("createBatch: null role + нет JWT → 403 Forbidden")
    void createBatch_givenNoRole_whenCalled_thenReturns403() {
        UUID productId = UUID.randomUUID();
        CreateBatchRequest req = new CreateBatchRequest(
                productId, "x", LocalDate.now(), LocalDate.now().plusMonths(6),
                "s", new BigDecimal("10"), null, null);

        ResponseEntity<BatchResponse> response = controller.createBatch(productId, req, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("getBatchesByProduct: возвращает Page<>")
    void getBatchesByProduct_givenProductId_whenCalled_thenReturnsPage() {
        UUID productId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        when(batchService.getBatchesByProduct(eq(productId), eq(orgId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample())));

        ResponseEntity<?> response = controller.getBatchesByProduct(productId, orgId, PageRequest.of(0, 20));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getBatchesByProduct: size > 100 ограничивается")
    void getBatchesByProduct_givenLargePageSize_whenCalled_thenCaps() {
        UUID productId = UUID.randomUUID();
        when(batchService.getBatchesByProduct(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        controller.getBatchesByProduct(productId, null, PageRequest.of(0, 500));

        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(batchService).getBatchesByProduct(any(), any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("getBatch: 200 OK")
    void getBatch_givenBatchId_whenCalled_thenReturns200() {
        UUID batchId = UUID.randomUUID();
        when(batchService.getBatch(eq(batchId), any())).thenReturn(sample());

        ResponseEntity<BatchResponse> response = controller.getBatch(batchId, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getAllBatches: возвращает Page<> с pagination")
    void getAllBatches_whenCalled_thenReturnsPage() {
        when(batchService.getAllBatches(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample())));

        ResponseEntity<?> response = controller.getAllBatches(UUID.randomUUID(), PageRequest.of(0, 20));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
