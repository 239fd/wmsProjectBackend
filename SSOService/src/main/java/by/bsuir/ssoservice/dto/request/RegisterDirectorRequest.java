package by.bsuir.ssoservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Регистрация директора без привязки к организации")
public record RegisterDirectorRequest(
        @Schema(description = "Email директора", example = "director@company.by")
        @NotBlank(message = "Email обязателен")
        @Email(message = "Некорректный формат email")
        String email,

        @Schema(description = "Пароль, минимум 8 символов", example = "Str0ngP@ssword")
        @NotBlank(message = "Пароль обязателен")
        @Size(min = 8, message = "Пароль должен содержать минимум 8 символов")
        String password,

        @Schema(description = "Имя директора", example = "Александр")
        @NotBlank(message = "Имя обязательно")
        String firstName,

        @Schema(description = "Фамилия директора", example = "Сидоров")
        @NotBlank(message = "Фамилия обязательна")
        String lastName,

        @Schema(description = "Отчество (необязательно)", example = "Петрович")
        String middleName
) {
    public String getFullName() {
        if (middleName != null && !middleName.isBlank()) {
            return lastName + " " + firstName + " " + middleName;
        }
        return lastName + " " + firstName;
    }
}
