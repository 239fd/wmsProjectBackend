package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.request.ReceiveProductRequest;
import by.bsuir.productservice.dto.request.ReserveProductRequest;
import by.bsuir.productservice.dto.request.ShipProductRequest;
import by.bsuir.productservice.service.ProductOperationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
public class OperationController {

    private final ProductOperationService operationService;




    @PostMapping("/receive")
    public ResponseEntity<Map<String, Object>> receiveProduct(
            @Valid @RequestBody ReceiveProductRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        UUID operationId = operationService.receiveProduct(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Товар принят на склад",
                "operationId", operationId
        ));
    }




    @PostMapping("/ship")
    public ResponseEntity<Map<String, Object>> shipProduct(
            @Valid @RequestBody ShipProductRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        UUID operationId = operationService.shipProduct(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Товар отгружен со склада",
                "operationId", operationId
        ));
    }




    @PostMapping("/reserve")
    public ResponseEntity<Map<String, String>> reserveProduct(
            @Valid @RequestBody ReserveProductRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        operationService.reserveProduct(request);

        return ResponseEntity.ok(Map.of("message", "Товар зарезервирован"));
    }




    @PostMapping("/release")
    public ResponseEntity<Map<String, String>> releaseReservation(
            @RequestParam UUID productId,
            @RequestParam UUID warehouseId,
            @RequestParam java.math.BigDecimal quantity,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        operationService.releaseReservation(productId, warehouseId, quantity);

        return ResponseEntity.ok(Map.of("message", "Резерв освобождён"));
    }





    @PostMapping("/ship-fefo")
    public ResponseEntity<Map<String, Object>> shipProductWithFEFO(
            @Valid @RequestBody ShipProductRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        UUID operationId = operationService.shipProductWithFEFO(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Товар отгружен со склада с автоматическим подбором партий по FEFO",
                "operationId", operationId,
                "method", "FEFO"
        ));
    }
}
