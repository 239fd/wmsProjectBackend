package by.bsuir.warehouseservice.controller;

import by.bsuir.warehouseservice.dto.request.CreateWarehouseRequest;
import by.bsuir.warehouseservice.dto.request.UpdateWarehouseRequest;
import by.bsuir.warehouseservice.dto.response.WarehouseResponse;
import by.bsuir.warehouseservice.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
@Tag(name = "Склады", description = "API для управления складами в системе WMS")
public class WarehouseController {

    private final WarehouseService warehouseService;

    @PostMapping
    public ResponseEntity<WarehouseResponse> createWarehouse(
            @Valid @RequestBody CreateWarehouseRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || (!"DIRECTOR".equals(userRole) && !"ACCOUNTANT".equals(userRole))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        WarehouseResponse response = warehouseService.createWarehouse(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{warehouseId}")
    public ResponseEntity<WarehouseResponse> getWarehouse(@PathVariable UUID warehouseId) {
        WarehouseResponse response = warehouseService.getWarehouse(warehouseId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/organization/{orgId}")
    public ResponseEntity<List<WarehouseResponse>> getWarehousesByOrganization(
            @PathVariable UUID orgId,
            @RequestParam(defaultValue = "false") boolean activeOnly) {

        List<WarehouseResponse> response = activeOnly
                ? warehouseService.getActiveWarehousesByOrganization(orgId)
                : warehouseService.getWarehousesByOrganization(orgId);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<WarehouseResponse>> getAllWarehouses() {
        List<WarehouseResponse> response = warehouseService.getAllWarehouses();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{warehouseId}")
    public ResponseEntity<WarehouseResponse> updateWarehouse(
            @PathVariable UUID warehouseId,
            @Valid @RequestBody UpdateWarehouseRequest request,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"DIRECTOR".equals(userRole) && !"ACCOUNTANT".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        WarehouseResponse response = warehouseService.updateWarehouse(warehouseId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{warehouseId}")
    public ResponseEntity<Map<String, String>> deleteWarehouse(
            @PathVariable UUID warehouseId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || !"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        warehouseService.deleteWarehouse(warehouseId);
        return ResponseEntity.ok(Map.of("message", "Склад успешно удалён"));
    }

    @PostMapping("/{warehouseId}/activate")
    public ResponseEntity<WarehouseResponse> activateWarehouse(
            @PathVariable UUID warehouseId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || !"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        WarehouseResponse response = warehouseService.activateWarehouse(warehouseId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{warehouseId}/deactivate")
    public ResponseEntity<WarehouseResponse> deactivateWarehouse(
            @PathVariable UUID warehouseId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || !"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        WarehouseResponse response = warehouseService.deactivateWarehouse(warehouseId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{warehouseId}/info")
    public ResponseEntity<Map<String, Object>> getWarehouseInfo(@PathVariable UUID warehouseId) {
        Map<String, Object> info = warehouseService.getWarehouseInfo(warehouseId);
        return ResponseEntity.ok(info);
    }
}
