package by.bsuir.organizationservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank(message = "Полное наименование обязательно")
        @Size(max = 255, message = "Полное наименование не должно превышать 255 символов")
        String name,

        @Size(max = 100, message = "Краткое наименование не должно превышать 100 символов")
        String shortName,

        @NotBlank(message = "УНП обязателен")
        @Size(max = 20, message = "УНП не должен превышать 20 символов")
        String unp,

        @Size(max = 512, message = "Адрес не должен превышать 512 символов")
        String address
) {
}
