package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.response.InventoryResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductReadModelRepository productReadModelRepository;

    @Transactional(readOnly = true)
    public List<InventoryResponse> getInventoryByWarehouse(UUID warehouseId) {
        return getInventoryByWarehouse(warehouseId, null);
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> getInventoryByProduct(UUID productId) {
        return getInventoryByProduct(productId, null);
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventoryByCell(UUID cellId) {
        return getInventoryByCell(cellId, null);
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> getInventoryByWarehouse(UUID warehouseId, UUID organizationId) {
        log.info("Getting inventory for warehouse: {} (org: {})", warehouseId, organizationId);
        List<Inventory> records = (organizationId != null)
                ? inventoryRepository.findByOrganizationIdAndWarehouseId(organizationId, warehouseId)
                : inventoryRepository.findByWarehouseId(warehouseId);
        return mapListWithProducts(records);
    }

    @Transactional(readOnly = true)
    public Page<InventoryResponse> getInventoryByWarehouse(UUID warehouseId, UUID organizationId, Pageable pageable) {
        Page<Inventory> records = (organizationId != null)
                ? inventoryRepository.findByOrganizationIdAndWarehouseId(organizationId, warehouseId, pageable)
                : inventoryRepository.findByWarehouseId(warehouseId, pageable);
        Map<UUID, ProductReadModel> productMap = loadProducts(records.getContent());
        return records.map(inv -> mapToResponse(inv, productMap.get(inv.getProductId())));
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> getInventoryByProduct(UUID productId, UUID organizationId) {
        log.info("Getting inventory for product: {} (org: {})", productId, organizationId);
        List<Inventory> records = (organizationId != null)
                ? inventoryRepository.findByOrganizationIdAndProductId(organizationId, productId)
                : inventoryRepository.findByProductId(productId);
        return mapListWithProducts(records);
    }

    @Transactional(readOnly = true)
    public Page<InventoryResponse> getInventoryByProduct(UUID productId, UUID organizationId, Pageable pageable) {
        Page<Inventory> records = (organizationId != null)
                ? inventoryRepository.findByOrganizationIdAndProductId(organizationId, productId, pageable)
                : inventoryRepository.findByProductId(productId, pageable);
        Map<UUID, ProductReadModel> productMap = loadProducts(records.getContent());
        return records.map(inv -> mapToResponse(inv, productMap.get(inv.getProductId())));
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventoryByCell(UUID cellId, UUID organizationId) {
        log.info("Getting inventory for cell: {} (org: {})", cellId, organizationId);
        Inventory inventory = (organizationId != null
                ? inventoryRepository.findByOrganizationIdAndCellId(organizationId, cellId)
                : inventoryRepository.findByCellId(cellId))
                .orElseThrow(() -> AppException.notFound("Запасы в ячейке не найдены"));
        ProductReadModel p = productReadModelRepository.findById(inventory.getProductId()).orElse(null);
        return mapToResponse(inventory, p);
    }

    private List<InventoryResponse> mapListWithProducts(List<Inventory> records) {
        Map<UUID, ProductReadModel> productMap = loadProducts(records);
        return records.stream()
                .map(inv -> mapToResponse(inv, productMap.get(inv.getProductId())))
                .collect(Collectors.toList());
    }

    private Map<UUID, ProductReadModel> loadProducts(Collection<Inventory> records) {
        Set<UUID> ids = records.stream()
                .map(Inventory::getProductId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return productReadModelRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ProductReadModel::getProductId, p -> p));
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

    private InventoryResponse mapToResponse(Inventory inventory, ProductReadModel product) {
        BigDecimal availableQty = inventory.getQuantity().subtract(inventory.getReservedQuantity());

        return new InventoryResponse(
                inventory.getInventoryId(),
                inventory.getProductId(),
                product != null ? product.getName() : null,
                product != null ? product.getSku() : null,
                inventory.getBatchId(),
                inventory.getOrganizationId(),
                inventory.getWarehouseId(),
                inventory.getCellId(),
                inventory.getUnitSku(),
                inventory.getQuantity(),
                inventory.getReservedQuantity(),
                availableQty,
                inventory.getStatus(),
                inventory.getLastUpdated()
        );
    }
}
