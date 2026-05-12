package by.bsuir.organizationservice.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateEmployeeStatusRequest(
        @NotNull(message = "Статус блокировки обязателен")
        Boolean blocked
) {
}