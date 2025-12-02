package by.bsuir.organizationservice.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record InvitationCodeResponse(
        String invitationCode,
        UUID warehouseId,
        String warehouseName,
        LocalDateTime expiresAt
) {
}
