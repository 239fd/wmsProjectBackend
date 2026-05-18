package by.bsuir.productservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ErpConnectionRequest(
        @NotBlank(message = "Тип агрегатора обязателен")
        @Pattern(regexp = "onec|api|rpa", message = "Допустимые значения: onec, api, rpa")
        String aggregator,

        String name,
        String username,
        String password,
        String basePath,
        String sectionName,
        String journalName,
        String driverUrl,
        Boolean isDefault
) {
}
