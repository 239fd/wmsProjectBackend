package by.bsuir.ssoservice.dto.request;

import by.bsuir.ssoservice.model.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;




public record CompleteOAuthRegistrationRequest(
        @NotBlank(message = "Временный токен обязателен")
        String temporaryToken,

        @NotNull(message = "Роль обязательна")
        UserRole role,

        String organizationId,
        String warehouseId
) {
}
