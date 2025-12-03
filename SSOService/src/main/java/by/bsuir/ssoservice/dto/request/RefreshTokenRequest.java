package by.bsuir.ssoservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token обязателен")
        String refreshToken
) {}
