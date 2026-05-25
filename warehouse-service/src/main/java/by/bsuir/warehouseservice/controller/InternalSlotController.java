package by.bsuir.warehouseservice.controller;

import by.bsuir.warehouseservice.service.SlotHeightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/internal/slots")
@RequiredArgsConstructor
@Tag(name = "Внутренний API слотов", description = "Inter-service: учёт оставшейся высоты ячейки/полки/паллет-места")
public class InternalSlotController {

    private final SlotHeightService slotHeightService;

    @Operation(summary = "Изменить остаток высоты слота (delta — со знаком)")
    @PostMapping("/{slotId}/height")
    public ResponseEntity<Map<String, Object>> adjustHeight(
            @PathVariable UUID slotId,
            @RequestBody Map<String, Object> body) {
        Object raw = body.get("delta");
        if (raw == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "delta обязателен"));
        }
        BigDecimal delta;
        try {
            delta = new BigDecimal(raw.toString());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "delta должен быть числом"));
        }
        BigDecimal remaining = slotHeightService.adjustHeight(slotId, delta);
        return ResponseEntity.ok(Map.of(
                "slotId", slotId.toString(),
                "delta", delta,
                "remainingHeightCm", remaining));
    }
}
