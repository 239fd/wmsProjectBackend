package by.bsuir.organizationservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

@Schema(description = "Запрос на создание приглашения для нового сотрудника")
public record CreateInvitationRequest(
        @Schema(description = "Email сотрудника, которому отправляется приглашение", example = "worker@company.by")
        @NotBlank(message = "Email обязателен")
        @Email(message = "Некорректный формат email")
        String email,

        @Schema(description = "Роль сотрудника: WORKER, ACCOUNTANT", example = "WORKER")
        @NotBlank(message = "Роль обязательна")
        String role,

        @Schema(description = "ID склада для привязки сотрудника (необязательно)", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID warehouseId
) {
}