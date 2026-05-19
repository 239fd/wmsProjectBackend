package by.bsuir.productservice.dto.response;

import by.bsuir.productservice.model.enums.DocumentLayout;
import by.bsuir.productservice.model.enums.DomesticDocumentKind;
import by.bsuir.productservice.model.enums.ShipmentRequestStatus;
import by.bsuir.productservice.model.enums.ShipmentType;

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
        ShipmentType shipmentType,
        String currency,
        DocumentLayout documentLayout,
        DomesticDocumentKind domesticDocumentKind,
        String recipientCountry,
        String recipientGln,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        BigDecimal progress,
        List<UUID> documentIds,
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
