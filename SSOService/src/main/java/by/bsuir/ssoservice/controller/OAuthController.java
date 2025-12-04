package by.bsuir.ssoservice.controller;

import by.bsuir.ssoservice.dto.request.CompleteOAuthRegistrationRequest;
import by.bsuir.ssoservice.dto.response.AuthResponse;
import by.bsuir.ssoservice.dto.response.OAuthRegistrationResponse;
import by.bsuir.ssoservice.service.OAuthService;
import by.bsuir.ssoservice.utils.RequestUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/oauth")
@RequiredArgsConstructor
@Tag(name = "OAuth2 аутентификация", description = "API для входа через Google и Yandex с помощью OAuth2")
public class OAuthController {

    private final OAuthService oauthService;

    @Operation(
            summary = "Инициировать OAuth2 аутентификацию",
            description = "Перенаправляет пользователя на страницу аутентификации провайдера (Google или Yandex)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "302", description = "Перенаправление на OAuth провайдера"),
            @ApiResponse(responseCode = "400", description = "Неподдерживаемый провайдер")
    })
    @GetMapping("/authorize/{provider}")
    public void initiateOAuth(
            @Parameter(description = "Провайдер OAuth (google, yandex)", required = true) @PathVariable String provider,
            @Parameter(description = "Тип операции (login, register)") @RequestParam(defaultValue = "login") String type,
            HttpServletResponse response) throws IOException {

        String authorizationUrl = oauthService.getAuthorizationUrl(provider, type);
        response.sendRedirect(authorizationUrl);
    }

    @Operation(
            summary = "Callback OAuth2",
            description = "Обрабатывает ответ от OAuth провайдера после авторизации пользователя"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "302", description = "Перенаправление на фронтенд с токенами"),
            @ApiResponse(responseCode = "400", description = "Ошибка аутентификации")
    })
    @GetMapping("/callback/{provider}")
    public void handleOAuthCallback(
            @Parameter(description = "Провайдер OAuth", required = true) @PathVariable String provider,
            @Parameter(description = "Код авторизации от провайдера") @RequestParam(required = false) String code,
            @Parameter(description = "State параметр для защиты от CSRF") @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String error_description,
            HttpServletRequest httpRequest,
            HttpServletResponse response) throws IOException {

        // Обработка ошибки от провайдера
        if (error != null) {
            String errorMessage = error_description != null ? error_description : error;
            String redirectUrl = String.format(
                    "http://localhost:3000/auth/callback?error=%s",
                    URLEncoder.encode(errorMessage, StandardCharsets.UTF_8)
            );
            response.sendRedirect(redirectUrl);
            return;
        }

        if (code == null) {
            String redirectUrl = "http://localhost:3000/auth/callback?error=" +
                    URLEncoder.encode("Не получен код авторизации", StandardCharsets.UTF_8);
            response.sendRedirect(redirectUrl);
            return;
        }

        try {
            Object result = oauthService.handleCallback(
                    provider,
                    code,
                    state,
                    RequestUtils.getClientIp(httpRequest),
                    RequestUtils.getUserAgent(httpRequest)
            );

            if (result instanceof AuthResponse authResponse) {
                // Существующий пользователь - отправляем токены
                String redirectUrl = String.format(
                        "http://localhost:3000/auth/callback?access_token=%s&refresh_token=%s",
                        URLEncoder.encode(authResponse.accessToken(), StandardCharsets.UTF_8),
                        URLEncoder.encode(authResponse.refreshToken(), StandardCharsets.UTF_8)
                );
                response.sendRedirect(redirectUrl);
            }
            else if (result instanceof OAuthRegistrationResponse regResponse) {
                // Новый пользователь - нужен выбор роли
                String redirectUrl = String.format(
                        "http://localhost:3000/auth/callback?token=%s&email=%s&name=%s",
                        URLEncoder.encode(regResponse.temporaryToken(), StandardCharsets.UTF_8),
                        URLEncoder.encode(regResponse.email() != null ? regResponse.email() : "", StandardCharsets.UTF_8),
                        URLEncoder.encode(regResponse.fullName() != null ? regResponse.fullName() : "", StandardCharsets.UTF_8)
                );
                response.sendRedirect(redirectUrl);
            }
        } catch (Exception e) {
            String redirectUrl = String.format(
                    "http://localhost:3000/auth/callback?error=%s",
                    URLEncoder.encode(e.getMessage() != null ? e.getMessage() : "Ошибка авторизации", StandardCharsets.UTF_8)
            );
            response.sendRedirect(redirectUrl);
        }
    }

    @PostMapping("/complete-registration")
    public ResponseEntity<AuthResponse> completeRegistration(
            @Valid @RequestBody CompleteOAuthRegistrationRequest request,
            HttpServletRequest httpRequest) {

        AuthResponse response = oauthService.completeRegistration(
                request,
                RequestUtils.getClientIp(httpRequest),
                RequestUtils.getUserAgent(httpRequest)
        );

        return ResponseEntity.ok(response);
    }
}
