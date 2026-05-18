package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.request.ErpConnectionRequest;
import by.bsuir.productservice.dto.response.ErpConnectionResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.service.ErpConnectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ErpConnectionController Tests")
class ErpConnectionControllerTest {

    @Mock
    private ErpConnectionService service;

    @InjectMocks
    private ErpConnectionController controller;

    private ErpConnectionRequest request;
    private ErpConnectionResponse response;

    @BeforeEach
    void setUp() {
        request = new ErpConnectionRequest(
                "onec", "x", "u", "p", "b", "s", "j", "d", true);
        response = new ErpConnectionResponse(
                UUID.randomUUID(), UUID.randomUUID(), "onec", "x", "u", true,
                "b", "s", "j", "d", true, UUID.randomUUID(),
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    @DisplayName("create: DIRECTOR → 201 Created")
    void create_givenDirectorRole_whenCalled_thenReturns201() {
        UUID userId = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        when(service.create(request, org, userId)).thenReturn(response);

        ResponseEntity<ErpConnectionResponse> result = controller.create(request, userId, "DIRECTOR", org);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("create: ACCOUNTANT → 201 Created")
    void create_givenAccountantRole_whenCalled_thenReturns201() {
        UUID userId = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        when(service.create(request, org, userId)).thenReturn(response);

        ResponseEntity<ErpConnectionResponse> result = controller.create(request, userId, "ACCOUNTANT", org);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("create: WORKER role → forbidden")
    void create_givenWorkerRole_whenCalled_thenForbidden() {
        assertThatThrownBy(() -> controller.create(request, UUID.randomUUID(), "WORKER", UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("DIRECTOR");
    }

    @Test
    @DisplayName("create: null role → forbidden")
    void create_givenNullRole_whenCalled_thenForbidden() {
        assertThatThrownBy(() -> controller.create(request, UUID.randomUUID(), null, UUID.randomUUID()))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("create: null userId → badRequest")
    void create_givenNullUserId_whenCalled_thenBadRequest() {
        assertThatThrownBy(() -> controller.create(request, null, "DIRECTOR", UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("X-User-Id");
    }

    @Test
    @DisplayName("list: DIRECTOR → 200 OK")
    void list_givenDirectorRole_whenCalled_thenReturns200() {
        UUID org = UUID.randomUUID();
        when(service.list(org)).thenReturn(List.of(response));

        ResponseEntity<List<ErpConnectionResponse>> result = controller.list("DIRECTOR", org);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("get: 200 OK")
    void get_givenDirectorRole_whenCalled_thenReturns200() {
        UUID connId = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        when(service.get(connId, org)).thenReturn(response);

        assertThat(controller.get(connId, "DIRECTOR", org).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("update: 200 OK")
    void update_givenDirectorRole_whenCalled_thenReturns200() {
        UUID connId = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        when(service.update(connId, request, org)).thenReturn(response);

        ResponseEntity<ErpConnectionResponse> result = controller.update(connId, request, "DIRECTOR", org);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("delete: 204 No Content")
    void delete_givenDirectorRole_whenCalled_thenReturns204() {
        UUID connId = UUID.randomUUID();
        UUID org = UUID.randomUUID();

        ResponseEntity<Void> result = controller.delete(connId, "DIRECTOR", org);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(connId, org);
    }

    @Test
    @DisplayName("setDefault: 200 OK")
    void setDefault_givenDirectorRole_whenCalled_thenReturns200() {
        UUID connId = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        when(service.setDefault(connId, org)).thenReturn(response);

        ResponseEntity<ErpConnectionResponse> result = controller.setDefault(connId, "DIRECTOR", org);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
