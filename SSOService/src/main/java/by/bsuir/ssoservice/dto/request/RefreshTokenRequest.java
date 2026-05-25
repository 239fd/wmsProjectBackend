package by.bsuir.ssoservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "Токен обновления обязателен")
        String refreshToken
) {}
