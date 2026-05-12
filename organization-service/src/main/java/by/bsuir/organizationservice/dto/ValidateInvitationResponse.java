package by.bsuir.organizationservice.dto;

import java.util.UUID;

public record ValidateInvitationResponse(
        Boolean valid,
        String organizationName,
        String role,
        String email,
        UUID organizationId,
        UUID warehouseId,
        String message
) {
    public ValidateInvitationResponse(boolean valid, String organizationName, String role, String email, String message) {
        this(valid, organizationName, role, email, null, null, message);
    }
}