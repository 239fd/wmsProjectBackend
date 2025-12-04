package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.request.ReceiveProductRequest;
import by.bsuir.productservice.dto.request.ReserveProductRequest;
import by.bsuir.productservice.dto.request.ShipProductRequest;
import by.bsuir.productservice.service.ProductOperationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Складские операции", description = "API для управления складскими операциями: приемка, отгрузка, резервирование товаров")
public class OperationController {

    private final ProductOperationService operationService;

    @Operation(
            summary = "Принять товар на склад",
            description = "Выполняет операцию приемки товара на склад с указанием партии и ячейки хранения"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Товар успешно принят"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    @PostMapping("/receive")
    public ResponseEntity<Map<String, Object>> receiveProduct(
            @Valid @RequestBody ReceiveProductRequest request,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        UUID operationId = operationService.receiveProduct(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Товар принят на склад",
                "operationId", operationId
        ));
    }

    @Operation(
            summary = "Отгрузить товар со склада",
            description = "Выполняет операцию отгрузки товара со склада с использованием FEFO логики (First Expired, First Out)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Товар успешно отгружен"),
            @ApiResponse(responseCode = "400", description = "Недостаточно товара или некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    @PostMapping("/ship")
    public ResponseEntity<Map<String, Object>> shipProduct(
            @Valid @RequestBody ShipProductRequest request,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        UUID operationId = operationService.shipProduct(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Товар отгружен со склада",
                "operationId", operationId
        ));
    }

    @Operation(
            summary = "Зарезервировать товар",
            description = "Резервирует указанное количество товара для последующей отгрузки"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Товар успешно зарезервирован"),
            @ApiResponse(responseCode = "400", description = "Недостаточно товара для резервирования"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    @PostMapping("/reserve")
    public ResponseEntity<Map<String, String>> reserveProduct(
            @Valid @RequestBody ReserveProductRequest request,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        operationService.reserveProduct(request);

        return ResponseEntity.ok(Map.of("message", "Товар зарезервирован"));
    }

    @Operation(
            summary = "Освободить резерв",
            description = "Освобождает ранее зарезервированное количество товара"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Резерв освобождён"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    @PostMapping("/release")
    public ResponseEntity<Map<String, String>> releaseReservation(
            @Parameter(description = "ID товара", required = true) @RequestParam UUID productId,
            @Parameter(description = "ID склада", required = true) @RequestParam UUID warehouseId,
            @Parameter(description = "Количество для освобождения", required = true) @RequestParam java.math.BigDecimal quantity,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        operationService.releaseReservation(productId, warehouseId, quantity);

        return ResponseEntity.ok(Map.of("message", "Резерв освобождён"));
    }

    @Operation(
            summary = "Отгрузить товар по FEFO",
            description = "Выполняет отгрузку товара с автоматическим подбором партий по принципу FEFO (First Expired, First Out)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Товар успешно отгружен по FEFO"),
            @ApiResponse(responseCode = "400", description = "Недостаточно товара или некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    @PostMapping("/ship-fefo")
    public ResponseEntity<Map<String, Object>> shipProductWithFEFO(
            @Valid @RequestBody ShipProductRequest request,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

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