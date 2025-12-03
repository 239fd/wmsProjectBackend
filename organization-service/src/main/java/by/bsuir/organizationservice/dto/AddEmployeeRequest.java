package by.bsuir.organizationservice.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddEmployeeRequest(
        @NotNull UUID userId,
        @NotNull String role
) {
}

