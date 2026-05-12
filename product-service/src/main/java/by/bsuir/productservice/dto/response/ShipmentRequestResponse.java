package by.bsuir.productservice.dto.response;

import by.bsuir.productservice.model.enums.ShipmentRequestStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ShipmentRequestResponse(
        UUID requestId,
        UUID organizationId,
        UUID warehouseId,
        String recipientName,
        String recipientAddress,
        String recipientInn,
        LocalDate plannedDate,
        String comment,
        ShipmentRequestStatus status,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        BigDecimal progress,
        List<Item> items
) {
    public record Item(
            UUID itemId,
            UUID productId,
            UUID batchId,
            BigDecimal expectedQty,
            BigDecimal pickedQty,
            String unitSku,
            String status
    ) {
    }
}
