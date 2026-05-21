package by.bsuir.productservice.dto.request;

import by.bsuir.productservice.model.enums.PackagingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Запрос на создание плановой поставки")
public record CreateSupplyRequest(
        UUID supplierId,

        @Schema(description = "Имя поставщика (если supplierId не задан)")
        String supplierName,

        UUID warehouseId,

        @Schema(description = "Плановая дата поступления")
        LocalDate expectedDate,

        @Schema(description = "Валюта")
        String currency,

        @Schema(description = "Общая сумма поставки")
        BigDecimal totalAmount,

        @Schema(description = "Примечания")
        String notes,

        UUID createdBy,

        @Schema(description = "Режим «только число позиций» — items не нужны, передайте totalItems")
        Boolean quantityOnly,

        @Schema(description = "Плановое число позиций (для quantity_only)")
        Integer totalItems,

        @Schema(description = "Позиции (для детального режима)")
        List<SupplyItemRequest> items
) {

    public record SupplyItemRequest(
            UUID productId,
            String productName,
            String sku,
            String barcode,
            String category,
            String unitOfMeasure,
            String manufacturer,
            String storageConditions,

            @NotNull(message = "Ожидаемое количество обязательно")
            BigDecimal expectedQty,

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
