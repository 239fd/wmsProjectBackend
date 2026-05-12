package by.bsuir.productservice.dto.request;

import by.bsuir.productservice.model.enums.AllocationStrategy;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateShipmentRequestRequest(
        @NotNull(message = "Warehouse ID обязателен")
        UUID warehouseId,

        String recipientName,
        String recipientAddress,
        String recipientInn,
        LocalDate plannedDate,
        String comment,

        AllocationStrategy strategy,

        @NotEmpty(message = "Хотя бы одна позиция обязательна")
        List<Item> items
) {
    public record Item(
            @NotNull(message = "Product ID обязателен")
            UUID productId,

            UUID batchId,

            @NotNull(message = "Количество обязательно")
            @Positive(message = "Количество должно быть положительным")
            BigDecimal expectedQty
    ) {
    }
}
