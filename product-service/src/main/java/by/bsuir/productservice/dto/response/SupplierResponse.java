package by.bsuir.productservice.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record SupplierResponse(
        UUID supplierId,
        UUID organizationId,
        String name,
        String unp,
        String contactPerson,
        String phone,
        String email,
        String address,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}