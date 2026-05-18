package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.request.CreateSupplyRequest;
import by.bsuir.productservice.dto.response.SupplyResponse;
import by.bsuir.productservice.model.enums.SupplyStatus;
import by.bsuir.productservice.service.SupplyService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupplyController Tests")
class SupplyControllerTest {

    @Mock
    private SupplyService supplyService;

    @InjectMocks
    private SupplyController controller;

    private SupplyResponse sample() {
        return new SupplyResponse(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), SupplyStatus.PLANNED, LocalDate.now(), null,
                1, "n", UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now(), List.of());
    }

    @Test
    @DisplayName("getAll: warehouseId задан → getByWarehouse")
    void getAll_givenWarehouseId_whenCalled_thenDelegatesToWarehouseFilter() {
        UUID wh = UUID.randomUUID();
        when(supplyService.getByWarehouse(eq(wh), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample())));

        controller.getAll(wh, null, UUID.randomUUID(), PageRequest.of(0, 20));

        verify(supplyService).getByWarehouse(eq(wh), any(Pageable.class));
    }

    @Test
    @DisplayName("getAll: orgId+status → getByOrganizationAndStatus")
    void getAll_givenOrgIdAndStatus_whenCalled_thenDelegatesToOrgStatus() {
        UUID org = UUID.randomUUID();
        when(supplyService.getByOrganizationAndStatus(eq(org), eq(SupplyStatus.PLANNED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        controller.getAll(null, SupplyStatus.PLANNED, org, PageRequest.of(0, 20));

        verify(supplyService).getByOrganizationAndStatus(eq(org), eq(SupplyStatus.PLANNED), any(Pageable.class));
    }

    @Test
    @DisplayName("getAll: только orgId → getByOrganization")
    void getAll_givenOnlyOrgId_whenCalled_thenDelegatesToOrgFilter() {
        UUID org = UUID.randomUUID();
        when(supplyService.getByOrganization(eq(org), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        controller.getAll(null, null, org, PageRequest.of(0, 20));

        verify(supplyService).getByOrganization(eq(org), any(Pageable.class));
    }

    @Test
    @DisplayName("getAll: только status → getByStatus")
    void getAll_givenOnlyStatus_whenCalled_thenDelegatesToStatusFilter() {
        when(supplyService.getByStatus(eq(SupplyStatus.IN_PROGRESS), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        controller.getAll(null, SupplyStatus.IN_PROGRESS, null, PageRequest.of(0, 20));

        verify(supplyService).getByStatus(eq(SupplyStatus.IN_PROGRESS), any(Pageable.class));
    }

    @Test
    @DisplayName("getAll: всё null → getAll(Pageable)")
    void getAll_givenNoFilters_whenCalled_thenDelegatesToGetAll() {
        when(supplyService.getAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.getAll(null, null, null, PageRequest.of(0, 20));

        verify(supplyService).getAll(any(Pageable.class));
    }

    @Test
    @DisplayName("getAll: page size > 100 → ограничивается до 100")
    void getAll_givenLargePageSize_whenCalled_thenCapsToMax() {
        when(supplyService.getAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.getAll(null, null, null, PageRequest.of(0, 500));

        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(supplyService).getAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("getById: 200 OK")
    void getById_givenSupplyId_whenCalled_thenReturns200() {
        UUID supplyId = UUID.randomUUID();
        when(supplyService.getById(supplyId)).thenReturn(sample());

        ResponseEntity<SupplyResponse> response = controller.getById(supplyId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("create: 201 Created")
    void create_givenValidRequest_whenCalled_thenReturns201() {
        UUID org = UUID.randomUUID();
        CreateSupplyRequest req = new CreateSupplyRequest(
                null, UUID.randomUUID(), LocalDate.now(), null, UUID.randomUUID(), null);
        when(supplyService.create(req, org)).thenReturn(sample());

        ResponseEntity<SupplyResponse> response = controller.create(req, org);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("updateStatus: парсит status+userId из body, делегирует service")
    void updateStatus_givenStatusBody_whenCalled_thenParsesAndDelegates() {
        UUID supplyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(supplyService.updateStatus(eq(supplyId), eq(SupplyStatus.IN_PROGRESS), eq(userId)))
                .thenReturn(sample());

        ResponseEntity<SupplyResponse> response = controller.updateStatus(
                supplyId, Map.of("status", "IN_PROGRESS", "userId", userId.toString()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(supplyService).updateStatus(supplyId, SupplyStatus.IN_PROGRESS, userId);
    }

    @Test
    @DisplayName("updateStatus: userId отсутствует в body → null")
    void updateStatus_givenNoUserId_whenCalled_thenPassesNull() {
        UUID supplyId = UUID.randomUUID();
        when(supplyService.updateStatus(eq(supplyId), eq(SupplyStatus.CANCELLED), eq(null)))
                .thenReturn(sample());

        controller.updateStatus(supplyId, Map.of("status", "CANCELLED"));

        verify(supplyService).updateStatus(supplyId, SupplyStatus.CANCELLED, null);
    }

    @Test
    @DisplayName("cancel: 200 OK с message")
    void cancel_givenSupplyId_whenCalled_thenDeletesAndReturnsMessage() {
        UUID supplyId = UUID.randomUUID();

        ResponseEntity<Map<String, String>> response = controller.cancel(supplyId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("message");
        verify(supplyService).delete(supplyId);
    }
}
