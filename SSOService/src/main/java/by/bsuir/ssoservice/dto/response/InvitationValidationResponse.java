package by.bsuir.ssoservice.dto.response;

import java.util.UUID;

public record InvitationValidationResponse(
        Boolean valid,
        String organizationName,
        String role,
        String email,
        UUID organizationId,
        UUID warehouseId,
        String message
) {
}