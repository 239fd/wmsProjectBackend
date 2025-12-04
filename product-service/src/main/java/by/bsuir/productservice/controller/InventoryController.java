package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.response.InventoryResponse;
import by.bsuir.productservice.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Остатки товаров", description = "API для получения информации об остатках товаров на складах и в ячейках хранения")
public class InventoryController {

    private final InventoryService inventoryService;

    @Operation(
            summary = "Получить остатки по складу",
            description = "Возвращает список всех товаров с их остатками на указанном складе"
    )
    @ApiResponse(responseCode = "200", description = "Остатки успешно получены")
    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<List<InventoryResponse>> getInventoryByWarehouse(
            @Parameter(description = "ID склада", required = true) @PathVariable UUID warehouseId) {
        List<InventoryResponse> response = inventoryService.getInventoryByWarehouse(warehouseId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Получить остатки товара",
            description = "Возвращает информацию об остатках конкретного товара на всех складах"
    )
    @ApiResponse(responseCode = "200", description = "Остатки успешно получены")
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<InventoryResponse>> getInventoryByProduct(
            @Parameter(description = "ID товара", required = true) @PathVariable UUID productId) {
        List<InventoryResponse> response = inventoryService.getInventoryByProduct(productId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Получить остатки в ячейке",
            description = "Возвращает информацию об остатках в конкретной ячейке хранения"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Остатки получены"),
            @ApiResponse(responseCode = "404", description = "Ячейка не найдена или пуста")
    })
    @GetMapping("/cell/{cellId}")
    public ResponseEntity<InventoryResponse> getInventoryByCell(
            @Parameter(description = "ID ячейки", required = true) @PathVariable UUID cellId) {
        InventoryResponse response = inventoryService.getInventoryByCell(cellId);
        return ResponseEntity.ok(response);
    }
}
