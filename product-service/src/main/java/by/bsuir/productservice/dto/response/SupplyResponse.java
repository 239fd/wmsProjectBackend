package by.bsuir.productservice.dto.response;

import by.bsuir.productservice.model.enums.PackagingType;
import by.bsuir.productservice.model.enums.StorageConditions;
import by.bsuir.productservice.model.enums.SupplyStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SupplyResponse(
        UUID supplyId,
        UUID organizationId,
        UUID supplierId,
        String supplierName,
        UUID warehouseId,
        SupplyStatus status,
        String externalId,
        String source,
        Boolean quantityOnly,
        LocalDate expectedDate,
        LocalDate actualDate,
        Integer totalItems,
        String currency,
        BigDecimal totalAmount,
        String notes,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<SupplyItemResponse> items
) {
    public record SupplyItemResponse(
            UUID itemId,
            UUID productId,
            Integer rowNumber,
            String productName,
            String sku,
            String barcode,
            String category,
            String unitOfMeasure,
            String manufacturer,
            StorageConditions storageConditions,
            BigDecimal expectedQty,
            BigDecimal actualQty,
            BigDecimal unitPrice,
            BigDecimal vatRate,
            BigDecimal vatAmount,
            BigDecimal totalAmount,
            PackagingType packagingType,
            String batchNumber,
            LocalDate manufactureDate,
            LocalDate expiryDate,
            BigDecimal purchasePrice,
            Boolean markedForWriteoff,
            String notes
    ) {
    }
}
