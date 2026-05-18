package by.bsuir.productservice.dto.response;

import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ReceiptSession;
import by.bsuir.productservice.model.enums.ReceiptSessionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ReceiptSessionResponse(
        UUID sessionId,
        UUID organizationId,
        UUID warehouseId,
        UUID supplierId,
        UUID supplyId,
        ReceiptSessionStatus status,
        UUID receiptOrderDocId,
        UUID receiptActDocId,
        UUID placementListDocId,
        String generalNotes,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        List<Item> items
) {
    public record Item(
            UUID operationId,
            UUID productId,
            UUID batchId,
            UUID cellId,
            BigDecimal quantity
    ) {
    }

    public static ReceiptSessionResponse from(ReceiptSession session, List<ProductOperation> ops) {
        List<Item> items = ops.stream()
                .map(op -> new Item(
                        op.getOperationId(),
                        op.getProductId(),
                        op.getBatchId(),
                        op.getToCellId(),
                        op.getQuantity()))
                .toList();
        return new ReceiptSessionResponse(
                session.getSessionId(),
                session.getOrganizationId(),
                session.getWarehouseId(),
                session.getSupplierId(),
                session.getSupplyId(),
                session.getStatus(),
                session.getReceiptOrderDocId(),
                session.getReceiptActDocId(),
                session.getPlacementListDocId(),
                session.getGeneralNotes(),
                session.getCreatedBy(),
                session.getCreatedAt(),
                session.getCompletedAt(),
                items);
    }
}
