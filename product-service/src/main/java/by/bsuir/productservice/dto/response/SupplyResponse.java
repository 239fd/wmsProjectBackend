package by.bsuir.productservice.dto.response;

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
        UUID warehouseId,
        SupplyStatus status,
        LocalDate expectedDate,
        LocalDate actualDate,
        Integer totalItems,
        String notes,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<SupplyItemResponse> items
) {
    public record SupplyItemResponse(
            UUID itemId,
            UUID productId,
            BigDecimal expectedQty,
            BigDecimal actualQty,
            BigDecimal unitPrice,
            String notes
    ) {
    }
}