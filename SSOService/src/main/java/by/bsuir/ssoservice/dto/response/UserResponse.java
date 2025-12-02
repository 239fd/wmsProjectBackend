package by.bsuir.ssoservice.dto.response;

import by.bsuir.ssoservice.model.enums.UserRole;

import java.util.UUID;




public record UserResponse(
        UUID userId,
        String email,
        String fullName,
        UserRole role,
        String photoBase64,
        UUID organizationId,
        UUID warehouseId
) {}
