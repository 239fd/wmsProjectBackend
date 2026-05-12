package by.bsuir.organizationservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record InvitationResponse(
        UUID invitationId,
        UUID invitationToken,
        String email,
        String role,
        UUID warehouseId,
        String inviteLink,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        Boolean used
) {
}