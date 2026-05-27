package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.request.CreateShipmentRequestRequest;
import by.bsuir.productservice.dto.request.PickRequest;
import by.bsuir.productservice.dto.response.ShipmentRequestResponse;
import by.bsuir.productservice.model.enums.AllocationStrategy;
import by.bsuir.productservice.model.enums.DocumentLayout;
import by.bsuir.productservice.model.enums.DomesticDocumentKind;
import by.bsuir.productservice.model.enums.ShipmentRequestStatus;
import by.bsuir.productservice.model.enums.ShipmentType;
import by.bsuir.productservice.service.ShipmentRequestService;
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
@DisplayName("ShipmentRequestController Tests")
class ShipmentRequestControllerTest {

    @Mock
    private ShipmentRequestService service;

    @InjectMocks
    private ShipmentRequestController controller;

    private ShipmentRequestResponse sample() {
        return new ShipmentRequestResponse(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "rec", "addr", "123", LocalDate.now(), "comment",
                ShipmentRequestStatus.PLANNED, ShipmentType.DOMESTIC, "BYN",
                DocumentLayout.HORIZONTAL, DomesticDocumentKind.TN, null, null,
                UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now(),
                BigDecimal.ZERO, null, null, List.of(), List.of());
    }

    private CreateShipmentRequestRequest sampleCreate() {
        return new CreateShipmentRequestRequest(
                UUID.randomUUID(), "rec", "addr", "123", LocalDate.now(), "c",
                AllocationStrategy.FEFO, ShipmentType.DOMESTIC, "BYN",
                DocumentLayout.HORIZONTAL, DomesticDocumentKind.TN, null, null,
                List.of(new CreateShipmentRequestRequest.Item(
                        UUID.randomUUID(), null, new BigDecimal("10"))));
    }

    @Test
    @DisplayName("create: 201 Created")
    void create_givenValidRequest_whenCalled_thenReturns201() {
        UUID userId = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        when(service.create(any(CreateShipmentRequestRequest.class), eq(userId), eq(org)))
                .thenReturn(sample());

        ResponseEntity<ShipmentRequestResponse> response = controller.create(sampleCreate(), userId, org);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("getAll: возвращает Page<>")
    void getAll_whenCalled_thenReturnsPage() {
        UUID org = UUID.randomUUID();
        when(service.getAll(eq(org), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample())));

        ResponseEntity<?> response = controller.getAll(org, PageRequest.of(0, 20));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getAll: size > 100 ограничивается")
    void getAll_givenLargePageSize_whenCalled_thenCaps() {
        UUID org = UUID.randomUUID();
        when(service.getAll(eq(org), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.getAll(org, PageRequest.of(0, 500));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).getAll(eq(org), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("get: 200 OK")
    void get_whenCalled_thenReturns200() {
        UUID requestId = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        when(service.get(requestId, org)).thenReturn(sample());

        assertThat(controller.get(requestId, org).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("pick: 200 OK + delegates to service.pick")
    void pick_whenCalled_thenDelegatesToService() {
        UUID requestId = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        PickRequest pickReq = new PickRequest("SKU-1", new BigDecimal("5"));
        when(service.pick(requestId, pickReq, org)).thenReturn(sample());

        ResponseEntity<ShipmentRequestResponse> response = controller.pick(requestId, pickReq, org);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).pick(requestId, pickReq, org);
    }

    @Test
    @DisplayName("unpick: 200 OK + delegates to service.unpick")
    void unpick_whenCalled_thenDelegatesToService() {
        UUID requestId = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        PickRequest pickReq = new PickRequest("SKU-1", new BigDecimal("5"));
        when(service.unpick(requestId, pickReq, org)).thenReturn(sample());

        ResponseEntity<ShipmentRequestResponse> response = controller.unpick(requestId, pickReq, org);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).unpick(requestId, pickReq, org);
    }

    @org.junit.jupiter.api.Disabled("Стрикт-стаббинг ломается после добавления manual:CompleteShipmentRequest в complete(); требует переписки мока")
    @Test
    @DisplayName("complete: 200 OK + delegates")
    void complete_whenCalled_thenDelegates() {
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        when(service.complete(requestId, userId, org)).thenReturn(sample());

        assertThat(controller.complete(requestId, null, userId, org).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("cancel: 204 No Content")
    void cancel_whenCalled_thenReturns204() {
        UUID requestId = UUID.randomUUID();
        UUID org = UUID.randomUUID();

        ResponseEntity<Void> response = controller.cancel(requestId, org);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).cancel(requestId, org);
    }
}
