package by.bsuir.organizationservice.dto.response;

import by.bsuir.organizationservice.model.enums.OrganizationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrganizationResponse(
        UUID orgId,
        String name,
        String shortName,
        String unp,
        String address,
        OrganizationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
