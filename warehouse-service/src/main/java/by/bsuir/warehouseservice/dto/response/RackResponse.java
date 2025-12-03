package by.bsuir.warehouseservice.dto.response;

import by.bsuir.warehouseservice.model.enums.RackKind;

import java.time.LocalDateTime;
import java.util.UUID;

public record RackResponse(
        UUID rackId,
        UUID warehouseId,
        RackKind kind,
        String name,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
