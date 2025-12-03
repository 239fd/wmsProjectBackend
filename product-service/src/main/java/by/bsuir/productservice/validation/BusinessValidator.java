package by.bsuir.productservice.validation;

import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductBatch;
import by.bsuir.productservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessValidator {

    private final InventoryRepository inventoryRepository;

public void validateInventoryAvailability(UUID productId, UUID warehouseId, BigDecimal requestedQuantity) {
        List<Inventory> inventoryList = inventoryRepository
                .findByProductIdAndWarehouseIdAndQuantityGreaterThan(productId, warehouseId, BigDecimal.ZERO);

        BigDecimal availableQuantity = inventoryList.stream()
                .map(Inventory::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (availableQuantity.compareTo(requestedQuantity) < 0) {
            throw AppException.badRequest(String.format(
                    "Недостаточно товара на складе. Доступно: %s, Запрошено: %s",
                    availableQuantity, requestedQuantity
            ));
        }

        log.info("Inventory availability validated: product={}, warehouse={}, available={}, requested={}",
                productId, warehouseId, availableQuantity, requestedQuantity);
    }

public void validateReservationAvailability(UUID productId, UUID warehouseId, BigDecimal requestedQuantity) {
        List<Inventory> inventoryList = inventoryRepository
                .findByProductIdAndWarehouseIdAndQuantityGreaterThan(productId, warehouseId, BigDecimal.ZERO);

        BigDecimal availableForReservation = inventoryList.stream()
                .map(inv -> inv.getQuantity().subtract(inv.getReservedQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (availableForReservation.compareTo(requestedQuantity) < 0) {
            throw AppException.badRequest(String.format(
                    "Недостаточно товара для резервирования. Доступно для резерва: %s, Запрошено: %s",
                    availableForReservation, requestedQuantity
            ));
        }

        log.info("Reservation availability validated: product={}, warehouse={}, availableForReserve={}, requested={}",
                productId, warehouseId, availableForReservation, requestedQuantity);
    }

public void validateWarehouseCapacity(UUID warehouseId, Integer currentOccupancy, Integer additionalQuantity, Integer maxCapacity) {
        int projectedOccupancy = currentOccupancy + additionalQuantity;

        if (projectedOccupancy > maxCapacity) {
            throw AppException.badRequest(String.format(
                    "Превышена вместимость склада. Текущая загрузка: %d, Добавляется: %d, Максимальная вместимость: %d",
                    currentOccupancy, additionalQuantity, maxCapacity
            ));
        }

        double occupancyPercentage = (projectedOccupancy * 100.0) / maxCapacity;
        if (occupancyPercentage > 95) {
            log.warn("Warehouse {} is nearly full: {}%", warehouseId, occupancyPercentage);
        }

        log.info("Warehouse capacity validated: warehouse={}, current={}, adding={}, max={}, occupancy={}%",
                warehouseId, currentOccupancy, additionalQuantity, maxCapacity, occupancyPercentage);
    }

public void validateBatchExpiry(ProductBatch batch) {
        if (batch.getExpiryDate() == null) {
            return;
        }

        LocalDate now = LocalDate.now();
        LocalDate expiryDate = batch.getExpiryDate();

        if (expiryDate.isBefore(now)) {
            throw AppException.badRequest(String.format(
                    "Партия %s просрочена (срок годности: %s)",
                    batch.getBatchNumber(), expiryDate
            ));
        }

        long daysUntilExpiry = java.time.temporal.ChronoUnit.DAYS.between(now, expiryDate);
        if (daysUntilExpiry <= 7) {
            log.warn("Batch {} expires soon: {} days left", batch.getBatchNumber(), daysUntilExpiry);
        }

        log.debug("Batch expiry validated: batch={}, expiryDate={}, daysLeft={}",
                batch.getBatchNumber(), expiryDate, daysUntilExpiry);
    }

public void validateBatchDates(LocalDate manufactureDate, LocalDate expiryDate) {
        if (manufactureDate == null || expiryDate == null) {
            return;
        }

        LocalDate now = LocalDate.now();

        if (manufactureDate.isAfter(now)) {
            throw AppException.badRequest("Дата производства не может быть в будущем");
        }

        if (expiryDate.isBefore(now)) {
            throw AppException.badRequest("Срок годности уже истек");
        }

        if (manufactureDate.isAfter(expiryDate)) {
            throw AppException.badRequest("Дата производства не может быть позже срока годности");
        }

        long shelfLife = java.time.temporal.ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        if (shelfLife < 1) {
            throw AppException.badRequest("Срок годности должен быть больше даты производства");
        }

        log.debug("Batch dates validated: manufacture={}, expiry={}, shelfLife={} days",
                manufactureDate, expiryDate, shelfLife);
    }

public void validatePositiveQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw AppException.badRequest("Количество должно быть больше нуля");
        }
    }

public void validatePositivePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw AppException.badRequest("Цена должна быть больше нуля");
        }
    }
}

