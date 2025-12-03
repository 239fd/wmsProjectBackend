package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.request.ReceiveProductRequest;
import by.bsuir.productservice.dto.request.ReserveProductRequest;
import by.bsuir.productservice.dto.request.ShipProductRequest;
import by.bsuir.productservice.service.ProductOperationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OperationController Unit Tests")
class OperationControllerTest {

    @Mock
    private ProductOperationService operationService;

    @InjectMocks
    private OperationController operationController;

    @Test
    @DisplayName("receiveProduct: Given valid role Should receive and return 201")
    void receiveProduct_GivenValidRole_ShouldReceiveAndReturn201() {
        UUID operationId = UUID.randomUUID();
        ReceiveProductRequest request = new ReceiveProductRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                UUID.randomUUID(),
                "Test notes"
        );

        when(operationService.receiveProduct(any(ReceiveProductRequest.class))).thenReturn(operationId);

        ResponseEntity<Map<String, Object>> response = operationController.receiveProduct(request, "ADMIN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("operationId");
        verify(operationService, times(1)).receiveProduct(any(ReceiveProductRequest.class));
    }

    @Test
    @DisplayName("receiveProduct: Given no role Should return 403")
    void receiveProduct_GivenNoRole_ShouldReturn403() {
        ReceiveProductRequest request = new ReceiveProductRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                UUID.randomUUID(),
                "Test notes"
        );

        ResponseEntity<Map<String, Object>> response = operationController.receiveProduct(request, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(operationService, never()).receiveProduct(any());
    }

    @Test
    @DisplayName("shipProduct: Given valid role Should ship and return 201")
    void shipProduct_GivenValidRole_ShouldShipAndReturn201() {
        UUID operationId = UUID.randomUUID();
        ShipProductRequest request = new ShipProductRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("50.00"),
                UUID.randomUUID(),
                "Test notes"
        );

        when(operationService.shipProduct(any(ShipProductRequest.class))).thenReturn(operationId);

        ResponseEntity<Map<String, Object>> response = operationController.shipProduct(request, "ADMIN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("operationId");
        verify(operationService, times(1)).shipProduct(any(ShipProductRequest.class));
    }

    @Test
    @DisplayName("shipProduct: Given no role Should return 403")
    void shipProduct_GivenNoRole_ShouldReturn403() {
        ShipProductRequest request = new ShipProductRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("50.00"),
                UUID.randomUUID(),
                "Test notes"
        );

        ResponseEntity<Map<String, Object>> response = operationController.shipProduct(request, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(operationService, never()).shipProduct(any());
    }

    @Test
    @DisplayName("reserveProduct: Given valid role Should reserve and return 200")
    void reserveProduct_GivenValidRole_ShouldReserveAndReturn200() {
        ReserveProductRequest request = new ReserveProductRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("25.00"),
                "Test notes"
        );

        doNothing().when(operationService).reserveProduct(any(ReserveProductRequest.class));

        ResponseEntity<Map<String, String>> response = operationController.reserveProduct(request, "ADMIN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("message");
        verify(operationService, times(1)).reserveProduct(any(ReserveProductRequest.class));
    }

    @Test
    @DisplayName("reserveProduct: Given no role Should return 403")
    void reserveProduct_GivenNoRole_ShouldReturn403() {
        ReserveProductRequest request = new ReserveProductRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("25.00"),
                "Test notes"
        );

        ResponseEntity<Map<String, String>> response = operationController.reserveProduct(request, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(operationService, never()).reserveProduct(any());
    }
}

