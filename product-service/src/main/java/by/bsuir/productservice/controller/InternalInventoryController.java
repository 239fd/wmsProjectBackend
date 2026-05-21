package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.response.CellLoadResponse;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.InventoryRepository.CellLoadProjection;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/internal/inventory")
@RequiredArgsConstructor
public class InternalInventoryController {

    private final InventoryRepository inventoryRepository;

    @PostMapping("/cells-load")
    public ResponseEntity<List<CellLoadResponse>> getCellsLoad(@RequestBody Map<String, List<UUID>> body) {
        List<UUID> cellIds = body == null ? null : body.get("cellIds");
        if (cellIds == null || cellIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<CellLoadProjection> rows = inventoryRepository.aggregateLoadByCellIds(cellIds);
        Map<UUID, CellLoadProjection> byCell = new HashMap<>();
        for (CellLoadProjection p : rows) {
            byCell.put(p.getCellId(), p);
        }

        List<CellLoadResponse> response = cellIds.stream()
                .map(id -> {
                    CellLoadProjection p = byCell.get(id);
                    int count = p == null ? 0 : p.getItemsCount().intValue();
                    BigDecimal total = p == null ? BigDecimal.ZERO : p.getTotalQuantity();
                    return new CellLoadResponse(id, count, total, count > 0);
                })
                .toList();

        return ResponseEntity.ok(response);
    }
}
