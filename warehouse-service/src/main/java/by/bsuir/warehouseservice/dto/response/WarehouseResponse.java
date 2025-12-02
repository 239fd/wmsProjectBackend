package by.bsuir.warehouseservice.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record WarehouseResponse(
        UUID warehouseId,
        UUID orgId,
        String name,
        String address,
        UUID responsibleUserId,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
