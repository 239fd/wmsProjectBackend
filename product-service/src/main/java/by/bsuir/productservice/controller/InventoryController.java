package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.response.InventoryResponse;
import by.bsuir.productservice.service.InventoryService;
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
public class InventoryController {

    private final InventoryService inventoryService;


    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<List<InventoryResponse>> getInventoryByWarehouse(@PathVariable UUID warehouseId) {
        List<InventoryResponse> response = inventoryService.getInventoryByWarehouse(warehouseId);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/product/{productId}")
    public ResponseEntity<List<InventoryResponse>> getInventoryByProduct(@PathVariable UUID productId) {
        List<InventoryResponse> response = inventoryService.getInventoryByProduct(productId);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/cell/{cellId}")
    public ResponseEntity<InventoryResponse> getInventoryByCell(@PathVariable UUID cellId) {
        InventoryResponse response = inventoryService.getInventoryByCell(cellId);
        return ResponseEntity.ok(response);
    }
}
