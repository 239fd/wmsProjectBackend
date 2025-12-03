package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.ReceiveProductRequest;
import by.bsuir.productservice.dto.request.ReserveProductRequest;
import by.bsuir.productservice.dto.request.ShipProductRequest;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.enums.InventoryStatus;
import by.bsuir.productservice.model.enums.OperationType;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductOperationService {

    private final ProductOperationRepository operationRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductReadModelRepository productRepository;
    private final InventoryService inventoryService;
    private final FEFOService fefoService;

    @Transactional
    public UUID receiveProduct(ReceiveProductRequest request) {
        try {
            log.info("Receiving product {} to warehouse {}, quantity: {}",
                    request.productId(), request.warehouseId(), request.quantity());

            if (request.productId() == null) {
                throw AppException.badRequest("Product ID обязателен");
            }
            if (request.warehouseId() == null) {
                throw AppException.badRequest("Warehouse ID обязателен");
            }
            if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw AppException.badRequest("Количество должно быть больше 0");
            }
            if (request.userId() == null) {
                throw AppException.badRequest("User ID обязателен");
            }

            ProductReadModel product = productRepository.findById(request.productId())
                    .orElseThrow(() -> AppException.notFound("Товар не найден"));

            ProductOperation operation = ProductOperation.builder()
                    .operationId(UUID.randomUUID())
                    .operationType(OperationType.RECEIPT)
                    .productId(request.productId())
                    .batchId(request.batchId())
                    .warehouseId(request.warehouseId())
                    .toCellId(request.cellId())
                    .quantity(request.quantity())
                    .userId(request.userId())
                    .operationDate(LocalDateTime.now())
                    .notes(request.notes())
                    .build();
            operationRepository.save(operation);

        Optional<Inventory> existingInventory = inventoryRepository
                .findByProductIdAndWarehouseId(request.productId(), request.warehouseId());

        if (existingInventory.isPresent()) {

            Inventory inventory = existingInventory.get();
            inventory.setQuantity(inventory.getQuantity().add(request.quantity()));
            inventory.setCellId(request.cellId());
            inventory.setBatchId(request.batchId());
            inventoryRepository.save(inventory);
            log.info("Updated existing inventory: {}", inventory.getInventoryId());
        } else {

            Inventory inventory = Inventory.builder()
                    .inventoryId(UUID.randomUUID())
                    .productId(request.productId())
                    .batchId(request.batchId())
                    .warehouseId(request.warehouseId())
                    .cellId(request.cellId())
                    .quantity(request.quantity())
                    .reservedQuantity(BigDecimal.ZERO)
                    .status(InventoryStatus.AVAILABLE)
                    .lastUpdated(LocalDateTime.now())
                    .build();
            inventoryRepository.save(inventory);
            log.info("Created new inventory: {}", inventory.getInventoryId());
        }

            log.info("Product received successfully. Operation ID: {}", operation.getOperationId());
            return operation.getOperationId();

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error receiving product: {}", e.getMessage(), e);
            throw AppException.internalError("Ошибка при приёмке товара: " + e.getMessage());
        }
    }

    @Transactional
    public UUID shipProduct(ShipProductRequest request) {
        try {
            log.info("Shipping product {} from warehouse {}, quantity: {}",
                    request.productId(), request.warehouseId(), request.quantity());

            if (request.productId() == null) {
                throw AppException.badRequest("Product ID обязателен");
            }
            if (request.warehouseId() == null) {
                throw AppException.badRequest("Warehouse ID обязателен");
            }
            if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw AppException.badRequest("Количество должно быть больше 0");
            }
            if (request.userId() == null) {
                throw AppException.badRequest("User ID обязателен");
            }

            ProductReadModel product = productRepository.findById(request.productId())
                    .orElseThrow(() -> AppException.notFound("Товар не найден"));

        Inventory inventory = inventoryRepository
                .findByProductIdAndWarehouseId(request.productId(), request.warehouseId())
                .orElseThrow(() -> AppException.notFound("Запасы товара на складе не найдены"));

        BigDecimal available = inventory.getQuantity().subtract(inventory.getReservedQuantity());
        if (available.compareTo(request.quantity()) < 0) {
            throw AppException.badRequest(
                    String.format("Недостаточно товара для отгрузки. Доступно: %s, запрошено: %s",
                            available, request.quantity())
            );
        }

        ProductOperation operation = ProductOperation.builder()
                .operationId(UUID.randomUUID())
                .operationType(OperationType.SHIPMENT)
                .productId(request.productId())
                .batchId(request.batchId())
                .warehouseId(request.warehouseId())
                .fromCellId(request.cellId())
                .quantity(request.quantity())
                .userId(request.userId())
                .operationDate(LocalDateTime.now())
                .notes(request.notes())
                .build();
        operationRepository.save(operation);

        inventory.setQuantity(inventory.getQuantity().subtract(request.quantity()));

        if (inventory.getQuantity().compareTo(BigDecimal.ZERO) == 0) {

            log.info("Inventory depleted for product {} at warehouse {}",
                    request.productId(), request.warehouseId());
        }

        inventoryRepository.save(inventory);

            log.info("Product shipped successfully. Operation ID: {}", operation.getOperationId());
            return operation.getOperationId();

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error shipping product: {}", e.getMessage(), e);
            throw AppException.internalError("Ошибка при отгрузке товара: " + e.getMessage());
        }
    }

    @Transactional
    public void reserveProduct(ReserveProductRequest request) {
        log.info("Reserving product {} at warehouse {}, quantity: {}",
                request.productId(), request.warehouseId(), request.quantity());

        inventoryService.reserve(request.productId(), request.warehouseId(), request.quantity());

        log.info("Product reserved successfully");
    }

    @Transactional
    public void releaseReservation(UUID productId, UUID warehouseId, BigDecimal quantity) {
        log.info("Releasing reservation for product {} at warehouse {}, quantity: {}",
                productId, warehouseId, quantity);

        inventoryService.releaseReservation(productId, warehouseId, quantity);

        log.info("Reservation released successfully");
    }

    @Transactional
    public UUID shipProductWithFEFO(ShipProductRequest request) {
        try {
            log.info("Shipping product {} from warehouse {} with FEFO, quantity: {}",
                    request.productId(), request.warehouseId(), request.quantity());

            if (request.productId() == null) {
                throw AppException.badRequest("Product ID обязателен");
            }
            if (request.warehouseId() == null) {
                throw AppException.badRequest("Warehouse ID обязателен");
            }
            if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw AppException.badRequest("Количество должно быть больше 0");
            }

            ProductReadModel product = productRepository.findById(request.productId())
                    .orElseThrow(() -> AppException.notFound("Товар не найден"));

            List<FEFOService.InventoryAllocation> allocations = fefoService.selectInventoryByFEFO(
                    request.productId(),
                    request.warehouseId(),
                    request.quantity()
            );

            log.info("FEFO selected {} allocations for shipment", allocations.size());

            UUID operationId = UUID.randomUUID();
            for (FEFOService.InventoryAllocation allocation : allocations) {

                ProductOperation operation = ProductOperation.builder()
                        .operationId(operationId)
                        .operationType(OperationType.SHIPMENT)
                        .productId(allocation.getProductId())
                        .batchId(allocation.getBatchId())
                        .warehouseId(allocation.getWarehouseId())
                        .fromCellId(allocation.getCellId())
                        .quantity(allocation.getQuantity())
                        .userId(request.userId())
                        .operationDate(LocalDateTime.now())
                        .notes(String.format("FEFO selection. Expiry: %s. %s",
                                allocation.getExpiryDate(),
                                request.notes() != null ? request.notes() : ""))
                        .build();
                operationRepository.save(operation);

                Inventory inventory = inventoryRepository.findById(allocation.getInventoryId())
                        .orElseThrow(() -> AppException.notFound("Inventory not found"));

                inventory.setQuantity(inventory.getQuantity().subtract(allocation.getQuantity()));
                inventory.setLastUpdated(LocalDateTime.now());
                inventoryRepository.save(inventory);

                log.info("Shipped {} units from batch {} (expiry: {})",
                        allocation.getQuantity(),
                        allocation.getBatchId(),
                        allocation.getExpiryDate());
            }

            log.info("Product shipped successfully with FEFO. Operation ID: {}", operationId);
            return operationId;

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error shipping product with FEFO: {}", e.getMessage(), e);
            throw AppException.internalError("Ошибка при отгрузке товара с FEFO: " + e.getMessage());
        }
    }
}
