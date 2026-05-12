package by.bsuir.productservice.dto.response;

import java.util.UUID;

public record PlacementResponse(
        UUID operationId,
        UUID inventoryId,
        UUID cellId,
        UUID rackId,
        String rackName,
        String storageConditions,
        String mode
) {
}
