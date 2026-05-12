package by.bsuir.ssoservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Регистрация по приглашению")
public record RegisterWithInvitationRequest(
        @Schema(description = "UUID токена приглашения из письма", example = "550e8400-e29b-41d4-a716-446655440000")
        @NotNull(message = "Invitation token обязателен")
        UUID invitationToken,

        @Schema(description = "Email (должен совпадать с email в приглашении)", example = "worker@company.by")
        @NotBlank(message = "Email обязателен")
        @Email(message = "Некорректный формат email")
        String email,

        @Schema(description = "Пароль, минимум 8 символов", example = "Str0ngP@ssword")
        @NotBlank(message = "Пароль обязателен")
        @Size(min = 8, message = "Пароль должен содержать минимум 8 символов")
        String password,

        @Schema(description = "Имя", example = "Иван")
        @NotBlank(message = "Имя обязательно")
        String firstName,

        @Schema(description = "Фамилия", example = "Петров")
        @NotBlank(message = "Фамилия обязательна")
        String lastName,

        @Schema(description = "Отчество (необязательно)", example = "Сергеевич")
        String middleName
) {
    public String getFullName() {
        if (middleName != null && !middleName.isBlank()) {
            return lastName + " " + firstName + " " + middleName;
        }
        return lastName + " " + firstName;
    }
}