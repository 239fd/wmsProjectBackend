package by.bsuir.productservice.dto.request;

import by.bsuir.productservice.model.enums.PackagingType;
import by.bsuir.productservice.model.enums.StorageConditions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateReceiptSessionRequest(
        @NotNull(message = "Склад обязателен")
        UUID warehouseId,

        UUID supplierId,
        UUID supplyId,

        @NotNull(message = "Пользователь обязателен")
        UUID userId,

        String generalNotes,

        String contractNumber,
        LocalDate contractDate,

        UUID responsibleUserId,
        List<UUID> commissionMembers,

        @NotEmpty(message = "Список позиций не может быть пустым")
        @Valid
        List<ReceiptItem> items
) {
    public record ReceiptItem(
            @NotNull(message = "Товар обязателен")
            UUID productId,

            UUID batchId,
            UUID cellId,

            @NotNull(message = "Количество обязательно")
            @Positive(message = "Количество должно быть положительным")
            BigDecimal quantity,

            BigDecimal pricePerUnit,
            String batchNumber,
            LocalDate expiryDate,
            PackagingType packagingType,
            Integer unitsPerPackage,
            BigDecimal packageLengthCm,
            BigDecimal packageWidthCm,
            BigDecimal packageHeightCm,
            BigDecimal packageWeightKg,
            UUID palletPlaceId,
            StorageConditions storageConditions,
            String palletType,
            String notes
    ) {
    }
}
