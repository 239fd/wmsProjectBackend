package by.bsuir.productservice.service;

import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.InventoryEvent;
import by.bsuir.productservice.model.enums.InventoryEventType;
import by.bsuir.productservice.repository.InventoryEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryEventService {

    private final InventoryEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryEvent record(UUID inventoryId, InventoryEventType type, Map<String, Object> payload) {
        if (inventoryId == null) {
            log.warn("Skipping inventory event {} — no inventoryId", type);
            return null;
        }
        int version = repository.findMaxEventVersionByInventoryId(inventoryId) + 1;
        Map<String, Object> safePayload = new HashMap<>();
        if (payload != null) {
            payload.forEach((k, v) -> {
                if (k != null && v != null) safePayload.put(k, v);
            });
        }
        JsonNode data = objectMapper.valueToTree(safePayload);
        InventoryEvent event = InventoryEvent.builder()
                .inventoryId(inventoryId)
                .eventType(type.name())
                .eventData(data)
                .eventVersion(version)
                .build();
        return repository.save(event);
    }

    public InventoryEvent recordQuantityChange(
            Inventory inventory,
            InventoryEventType type,
            BigDecimal quantityBefore,
            BigDecimal quantityDelta,
            UUID operationId,
            UUID userId,
            Map<String, Object> extra) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", inventory.getProductId());
        payload.put("batchId", inventory.getBatchId());
        payload.put("warehouseId", inventory.getWarehouseId());
        payload.put("cellId", inventory.getCellId());
        payload.put("quantityBefore", quantityBefore);
        payload.put("quantityAfter", inventory.getQuantity());
        payload.put("quantityDelta", quantityDelta);
        payload.put("operationId", operationId);
        payload.put("userId", userId);
        if (extra != null) payload.putAll(extra);
        return record(inventory.getInventoryId(), type, payload);
    }
}
