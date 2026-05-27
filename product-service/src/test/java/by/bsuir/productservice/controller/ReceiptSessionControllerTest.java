package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.request.CreateReceiptSessionRequest;
import by.bsuir.productservice.dto.request.SessionDiscrepancyRequest;
import by.bsuir.productservice.dto.response.ReceiptSessionResponse;
import by.bsuir.productservice.model.entity.ReceiptSession;
import by.bsuir.productservice.model.enums.ReceiptSessionStatus;
import by.bsuir.productservice.service.ReceiptSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReceiptSessionController Tests")
class ReceiptSessionControllerTest {

    @Mock
    private ReceiptSessionService sessionService;

    @InjectMocks
    private ReceiptSessionController controller;

    private ReceiptSession session(UUID id, ReceiptSessionStatus status) {
        return ReceiptSession.builder()
                .sessionId(id)
                .organizationId(UUID.randomUUID())
                .warehouseId(UUID.randomUUID())
                .status(status)
                .build();
    }

    private CreateReceiptSessionRequest sampleCreateRequest() {
        return new CreateReceiptSessionRequest(
                UUID.randomUUID(), null, null, UUID.randomUUID(), "note",
                null, null, null, null,
                List.of(new CreateReceiptSessionRequest.ReceiptItem(
                        UUID.randomUUID(), null, null,
                        new BigDecimal("10"), null,
                        "BATCH-001", LocalDate.now().plusMonths(6),
                        null, null, null, null, null, null, null, null, null, "n")));
    }

    @Test
    @DisplayName("createSession: WORKER → 201 Created")
    void createSession_givenWorkerRole_whenCalled_thenReturns201() {
        UUID sessionId = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        when(sessionService.createSession(any(), eq(org))).thenReturn(session(sessionId, ReceiptSessionStatus.PAUSED));
        when(sessionService.getSessionOperations(sessionId)).thenReturn(List.of());

        ResponseEntity<ReceiptSessionResponse> response =
                controller.createSession(sampleCreateRequest(), "WORKER", org);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("createSession: null role → 403 Forbidden")
    void createSession_givenNullRole_whenCalled_thenForbidden() {
        ResponseEntity<ReceiptSessionResponse> response =
                controller.createSession(sampleCreateRequest(), null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("completeSession: WORKER → 200 OK")
    void completeSession_givenWorkerRole_whenCalled_thenReturns200() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        when(sessionService.completeSession(sessionId, userId, org))
                .thenReturn(session(sessionId, ReceiptSessionStatus.COMPLETED));
        when(sessionService.getSessionOperations(sessionId)).thenReturn(List.of());

        ResponseEntity<ReceiptSessionResponse> response =
                controller.completeSession(sessionId, userId, "WORKER", org);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("completeSession: null role → 403 Forbidden")
    void completeSession_givenNullRole_whenCalled_thenForbidden() {
        assertThat(controller.completeSession(UUID.randomUUID(), null, null, null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("recordDiscrepancy: WORKER → 200 OK")
    void recordDiscrepancy_givenWorkerRole_whenCalled_thenReturns200() {
        UUID sessionId = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        SessionDiscrepancyRequest req = new SessionDiscrepancyRequest(
                UUID.randomUUID(), "fix",
                List.of(new SessionDiscrepancyRequest.DiscrepancyItem(
                        UUID.randomUUID(), null, new BigDecimal("10"), new BigDecimal("7"),
                        "тара", "SHORTAGE")));
        when(sessionService.recordDiscrepancy(eq(sessionId), eq(req), eq(org)))
                .thenReturn(session(sessionId, ReceiptSessionStatus.COMPLETED_WITH_DISCREPANCY));
        when(sessionService.getSessionOperations(sessionId)).thenReturn(List.of());

        ResponseEntity<ReceiptSessionResponse> response =
                controller.recordDiscrepancy(sessionId, req, "WORKER", org);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("recordDiscrepancy: null role → 403 Forbidden")
    void recordDiscrepancy_givenNullRole_whenCalled_thenForbidden() {
        SessionDiscrepancyRequest req = new SessionDiscrepancyRequest(UUID.randomUUID(), null, List.of());
        assertThat(controller.recordDiscrepancy(UUID.randomUUID(), req, null, null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("listSessions: 200 OK с Page<>")
    void listSessions_whenCalled_thenReturns200() {
        UUID org = UUID.randomUUID();
        when(sessionService.listSessions(eq(org), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(session(UUID.randomUUID(), ReceiptSessionStatus.PAUSED))));
        when(sessionService.getSessionOperations(any())).thenReturn(List.of());

        ResponseEntity<?> response = controller.listSessions(
                null, null, PageRequest.of(0, 20), org);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("listSessions: size > 100 ограничивается")
    void listSessions_givenLargePageSize_whenCalled_thenCaps() {
        UUID org = UUID.randomUUID();
        when(sessionService.listSessions(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        controller.listSessions(null, null, PageRequest.of(0, 500), org);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        Mockito.verify(sessionService).listSessions(any(), any(), any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("getSession: 200 OK")
    void getSession_whenCalled_thenReturns200() {
        UUID sessionId = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        when(sessionService.getSession(sessionId, org))
                .thenReturn(session(sessionId, ReceiptSessionStatus.PAUSED));
        when(sessionService.getSessionOperations(sessionId)).thenReturn(List.of());

        ResponseEntity<ReceiptSessionResponse> response = controller.getSession(sessionId, org);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
