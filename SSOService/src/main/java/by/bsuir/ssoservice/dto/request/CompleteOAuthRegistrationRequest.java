package by.bsuir.ssoservice.dto.request;

import by.bsuir.ssoservice.model.enums.UserRole;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CompleteOAuthRegistrationRequest(
        @NotBlank(message = "Временный токен обязателен")
        String temporaryToken,


        UserRole role,

        String organizationId,
        String warehouseId,

        UUID invitationToken
) {
}
