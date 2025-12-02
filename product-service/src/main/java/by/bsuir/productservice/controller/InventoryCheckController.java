package by.bsuir.productservice.controller;

import by.bsuir.productservice.service.InventoryCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;




@Slf4j
@RestController
@RequestMapping("/api/inventory-check")
@RequiredArgsConstructor
public class InventoryCheckController {

    private final InventoryCheckService inventoryCheckService;




    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startInventory(
            @RequestParam UUID warehouseId,
            @RequestParam UUID userId,
            @RequestParam(required = false) String notes,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || (!"DIRECTOR".equals(userRole) && !"ACCOUNTANT".equals(userRole))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        UUID sessionId = inventoryCheckService.startInventory(warehouseId, userId, notes);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "sessionId", sessionId.toString(),
                "message", "Инвентаризация начата",
                "warehouseId", warehouseId.toString()
        ));
    }




    @PostMapping("/{sessionId}/record")
    public ResponseEntity<Map<String, String>> recordActualCount(
            @PathVariable UUID sessionId,
            @RequestParam UUID productId,
            @RequestParam(required = false) UUID cellId,
            @RequestParam BigDecimal actualQuantity,
            @RequestParam(required = false) String notes,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        inventoryCheckService.recordActualCount(sessionId, productId, cellId, actualQuantity, notes);

        return ResponseEntity.ok(Map.of(
                "message", "Фактическое количество зафиксировано",
                "sessionId", sessionId.toString(),
                "productId", productId.toString(),
                "actualQuantity", actualQuantity.toString()
        ));
    }




    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<Map<String, Object>> completeInventory(
            @PathVariable UUID sessionId,
            @RequestParam UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || (!"DIRECTOR".equals(userRole) && !"ACCOUNTANT".equals(userRole))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, Object> result = inventoryCheckService.completeInventory(sessionId, userId);

        return ResponseEntity.ok(result);
    }




    @PostMapping("/{sessionId}/cancel")
    public ResponseEntity<Map<String, String>> cancelInventory(
            @PathVariable UUID sessionId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || (!"DIRECTOR".equals(userRole) && !"ACCOUNTANT".equals(userRole))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        inventoryCheckService.cancelInventory(sessionId);

        return ResponseEntity.ok(Map.of(
                "message", "Инвентаризация отменена",
                "sessionId", sessionId.toString()
        ));
    }




    @GetMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> getInventorySession(@PathVariable UUID sessionId) {
        Map<String, Object> session = inventoryCheckService.getInventorySession(sessionId);
        return ResponseEntity.ok(session);
    }
}
