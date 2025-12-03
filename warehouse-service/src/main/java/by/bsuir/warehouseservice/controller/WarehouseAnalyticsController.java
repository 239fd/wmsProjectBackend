package by.bsuir.warehouseservice.controller;

import by.bsuir.warehouseservice.service.WarehouseAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/warehouses/analytics")
@RequiredArgsConstructor
public class WarehouseAnalyticsController {

    private final WarehouseAnalyticsService analyticsService;

    @GetMapping("/{warehouseId}")
    public ResponseEntity<Map<String, Object>> getWarehouseAnalytics(
            @PathVariable UUID warehouseId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || !"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> analytics = analyticsService.getWarehouseAnalytics(warehouseId);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/organization/{orgId}/summary")
    public ResponseEntity<Map<String, Object>> getOrganizationWarehousesSummary(
            @PathVariable UUID orgId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || !"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> summary = analyticsService.getOrganizationWarehousesSummary(orgId);
        return ResponseEntity.ok(summary);
    }
}

