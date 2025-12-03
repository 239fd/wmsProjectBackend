package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.response.InventoryResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public List<InventoryResponse> getInventoryByWarehouse(UUID warehouseId) {
        log.info("Getting inventory for warehouse: {}", warehouseId);
        return inventoryRepository.findByWarehouseId(warehouseId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> getInventoryByProduct(UUID productId) {
        log.info("Getting inventory for product: {}", productId);
        return inventoryRepository.findByProductId(productId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventoryByCell(UUID cellId) {
        log.info("Getting inventory for cell: {}", cellId);
        Inventory inventory = inventoryRepository.findByCellId(cellId)
                .orElseThrow(() -> AppException.notFound("Запасы в ячейке не найдены"));
        return mapToResponse(inventory);
    }

    @Transactional
    public void reserve(UUID productId, UUID warehouseId, BigDecimal quantity) {
        log.info("Reserving {} of product {} at warehouse {}", quantity, productId, warehouseId);

        Inventory inventory = inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow(() -> AppException.notFound("Запасы товара на складе не найдены"));

        BigDecimal available = inventory.getQuantity().subtract(inventory.getReservedQuantity());
        if (available.compareTo(quantity) < 0) {
            throw AppException.badRequest("Недостаточно товара для резервирования. Доступно: " + available);
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity().add(quantity));
        inventoryRepository.save(inventory);

        log.info("Reserved successfully");
    }

    @Transactional
    public void releaseReservation(UUID productId, UUID warehouseId, BigDecimal quantity) {
        log.info("Releasing reservation {} of product {} at warehouse {}", quantity, productId, warehouseId);

        Inventory inventory = inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow(() -> AppException.notFound("Запасы товара на складе не найдены"));

        if (inventory.getReservedQuantity().compareTo(quantity) < 0) {
            throw AppException.badRequest("Попытка освободить больше чем зарезервировано");
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity().subtract(quantity));
        inventoryRepository.save(inventory);

        log.info("Reservation released successfully");
    }

    private InventoryResponse mapToResponse(Inventory inventory) {
        BigDecimal availableQty = inventory.getQuantity().subtract(inventory.getReservedQuantity());

        return new InventoryResponse(
                inventory.getInventoryId(),
                inventory.getProductId(),
                inventory.getBatchId(),
                inventory.getWarehouseId(),
                inventory.getCellId(),
                inventory.getQuantity(),
                inventory.getReservedQuantity(),
                availableQty,
                inventory.getStatus(),
                inventory.getLastUpdated()
        );
    }
}
