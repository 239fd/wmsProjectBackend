package by.bsuir.ssoservice.controller;

import by.bsuir.ssoservice.dto.request.LoginRequest;
import by.bsuir.ssoservice.dto.request.RefreshTokenRequest;
import by.bsuir.ssoservice.dto.request.RegisterRequest;
import by.bsuir.ssoservice.dto.response.AuthResponse;
import by.bsuir.ssoservice.dto.response.UserResponse;
import by.bsuir.ssoservice.service.JwtTokenService;
import by.bsuir.ssoservice.service.UserService;
import by.bsuir.ssoservice.utils.RequestUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtTokenService jwtTokenService;

    @Operation(
            summary = "Регистрация пользователя",
            description = "Создает нового пользователя в системе и возвращает JWT токены для аутентификации"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Пользователь успешно зарегистрирован",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректные данные или email уже используется"),
            @ApiResponse(responseCode = "409", description = "Пользователь с таким email уже существует")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = userService.register(
                request,
                RequestUtils.getClientIp(httpRequest),
                RequestUtils.getUserAgent(httpRequest)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Вход в систему",
            description = "Аутентифицирует пользователя по email и паролю, возвращает JWT токены"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешная аутентификация",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Неверный email или пароль"),
            @ApiResponse(responseCode = "403", description = "Аккаунт заблокирован")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = userService.login(
                request,
                RequestUtils.getClientIp(httpRequest),
                RequestUtils.getUserAgent(httpRequest)
        );
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Обновить токены",
            description = "Обновляет access token используя refresh token"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Токены успешно обновлены",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Невалидный или истекший refresh token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = userService.refreshToken(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Выход из системы",
            description = "Выполняет выход пользователя, инвалидирует refresh token"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешный выход"),
            @ApiResponse(responseCode = "401", description = "Невалидный токен")
    })
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody RefreshTokenRequest request) {
        userService.logout(request.refreshToken());
        return ResponseEntity.ok(Map.of("message", "Успешный выход"));
    }

    @Operation(
            summary = "Получить текущего пользователя",
            description = "Возвращает информацию о текущем аутентифицированном пользователе"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Информация о пользователе получена",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Неавторизован")
    })
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            @Parameter(description = "Bearer токен", required = true) @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        UUID userId = jwtTokenService.getUserIdFromToken(token);
        UserResponse response = userService.getUserInfo(userId);
        return ResponseEntity.ok(response);
    }
}
