package by.bsuir.productservice.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record ErpConnectionResponse(
        UUID connectionId,
        UUID organizationId,
        String aggregator,
        String name,
        String username,
        boolean hasPassword,
        String basePath,
        String sectionName,
        String journalName,
        String driverUrl,
        boolean isDefault,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
