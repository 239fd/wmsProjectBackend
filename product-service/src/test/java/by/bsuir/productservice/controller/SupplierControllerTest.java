package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.request.CreateSupplierRequest;
import by.bsuir.productservice.dto.response.SupplierResponse;
import by.bsuir.productservice.service.SupplierService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupplierController Tests")
class SupplierControllerTest {

    @Mock
    private SupplierService service;

    @InjectMocks
    private SupplierController controller;

    private SupplierResponse sample(UUID id) {
        return new SupplierResponse(id, UUID.randomUUID(), "X", "1", "p", "+1",
                "e", "a", true, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    @DisplayName("getAll: возвращает Page<>, ограничивает size до 100")
    void getAll_givenLargePageSize_whenCalled_thenCapsToMax() {
        UUID orgId = UUID.randomUUID();
        Pageable hugePage = PageRequest.of(0, 1000);
        when(service.getAll(eq(orgId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(sample(UUID.randomUUID()))));

        ResponseEntity<?> response = controller.getAll(orgId, hugePage);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).getAll(eq(orgId), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("getAll: size в пределах ≤100 — передаётся как есть")
    void getAll_givenNormalSize_whenCalled_thenPassesThrough() {
        Pageable small = PageRequest.of(2, 50);
        when(service.getAll(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of()));

        controller.getAll(null, small);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).getAll(any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("getById: 200 OK с телом")
    void getById_givenValidIds_whenCalled_thenReturns200() {
        UUID supplierId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        SupplierResponse expected = sample(supplierId);
        when(service.getById(supplierId, orgId)).thenReturn(expected);

        ResponseEntity<SupplierResponse> response = controller.getById(supplierId, orgId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    @DisplayName("create: 201 Created с телом")
    void create_givenValidRequest_whenCalled_thenReturns201() {
        UUID orgId = UUID.randomUUID();
        CreateSupplierRequest req = new CreateSupplierRequest(
                "X", "1", "p", "+1", "e", "a");
        SupplierResponse expected = sample(UUID.randomUUID());
        when(service.create(req, orgId)).thenReturn(expected);

        ResponseEntity<SupplierResponse> response = controller.create(req, orgId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    @DisplayName("update: 200 OK")
    void update_givenValidRequest_whenCalled_thenReturns200() {
        UUID supplierId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        CreateSupplierRequest req = new CreateSupplierRequest(
                "X", "1", "p", "+1", "e", "a");
        when(service.update(supplierId, req, orgId)).thenReturn(sample(supplierId));

        ResponseEntity<SupplierResponse> response = controller.update(supplierId, req, orgId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).update(supplierId, req, orgId);
    }

    @Test
    @DisplayName("delete: 204 No Content")
    void delete_givenValidIds_whenCalled_thenReturns204() {
        UUID supplierId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        ResponseEntity<Void> response = controller.delete(supplierId, orgId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(supplierId, orgId);
    }
}
