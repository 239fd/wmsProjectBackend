package by.bsuir.productservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferProductRequest(
        @NotNull(message = "Product ID обязателен")
        UUID productId,

        @NotNull(message = "Warehouse ID отправителя обязателен")
        UUID fromWarehouseId,

        @NotNull(message = "Warehouse ID получателя обязателен")
        UUID toWarehouseId,

        UUID fromCellId,

        UUID toCellId,

        UUID batchId,

        @NotNull(message = "Количество обязательно")
        @DecimalMin(value = "0.001", message = "Количество должно быть больше 0")
        BigDecimal quantity,

        @NotNull(message = "User ID обязателен")
        UUID userId,

        String notes
) {
}

