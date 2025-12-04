package by.bsuir.ssoservice.integration;

import by.bsuir.ssoservice.controller.AuthController;
import by.bsuir.ssoservice.dto.request.LoginRequest;
import by.bsuir.ssoservice.dto.request.RegisterRequest;
import by.bsuir.ssoservice.dto.response.AuthResponse;
import by.bsuir.ssoservice.dto.response.UserResponse;
import by.bsuir.ssoservice.model.enums.UserRole;
import by.bsuir.ssoservice.service.JwtTokenService;
import by.bsuir.ssoservice.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты для AuthController.
 *
 * Используют @WebMvcTest для тестирования веб-слоя:
 * - Поднимается только MVC контекст (контроллеры, фильтры, валидация)
 * - Сервисы мокируются через @MockBean
 * - Тестируется HTTP взаимодействие через MockMvc
 *
 * Что тестируется:
 * - Маршрутизация HTTP запросов
 * - Валидация входных данных (@Valid)
 * - Сериализация/десериализация JSON
 * - HTTP статус-коды ответов
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController Integration Tests")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtTokenService jwtTokenService;

    private static final String REGISTER_URL = "/api/auth/register";
    private static final String LOGIN_URL = "/api/auth/login";
    private static final String ME_URL = "/api/auth/me";

    @Nested
    @DisplayName("POST /api/auth/register - Регистрация пользователя")
    class RegisterTests {

        @Test
        @DisplayName("Успешная регистрация DIRECTOR - возвращает 201 и токены")
        void register_WithValidDirectorRequest_ShouldReturnCreatedWithTokens() throws Exception {
            // Given
            RegisterRequest request = new RegisterRequest(
                    "director@test.com",
                    "Иван",
                    "Иванов",
                    "Иванович",
                    "password123",
                    UserRole.DIRECTOR,
                    null
            );

            AuthResponse expectedResponse = AuthResponse.of(
                    "access-token-123",
                    "refresh-token-456",
                    900L
            );

            when(userService.register(any(RegisterRequest.class), nullable(String.class), nullable(String.class)))
                    .thenReturn(expectedResponse);

            // When & Then
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken").value("access-token-123"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token-456"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(900));

            verify(userService).register(any(RegisterRequest.class), nullable(String.class), nullable(String.class));
        }

        @Test
        @DisplayName("Успешная регистрация WORKER - возвращает 201")
        void register_WithValidWorkerRequest_ShouldReturnCreated() throws Exception {
            // Given
            RegisterRequest request = new RegisterRequest(
                    "worker@test.com",
                    "Петр",
                    "Петров",
                    null,
                    "password123",
                    UserRole.WORKER,
                    "ORG-001"
            );

            AuthResponse expectedResponse = AuthResponse.of(
                    "worker-access-token",
                    "worker-refresh-token",
                    900L
            );

            when(userService.register(any(RegisterRequest.class), nullable(String.class), nullable(String.class)))
                    .thenReturn(expectedResponse);

            // When & Then
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken").exists());
        }

        @Test
        @DisplayName("Ошибка валидации - некорректный email, возвращает 400")
        void register_WithInvalidEmail_ShouldReturnBadRequest() throws Exception {
            // Given
            RegisterRequest request = new RegisterRequest(
                    "invalid-email",
                    "Test",
                    "User",
                    null,
                    "password123",
                    UserRole.DIRECTOR,
                    null
            );

            // When & Then
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).register(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("Ошибка валидации - короткий пароль, возвращает 400")
        void register_WithShortPassword_ShouldReturnBadRequest() throws Exception {
            // Given
            RegisterRequest request = new RegisterRequest(
                    "test@test.com",
                    "Test",
                    "User",
                    null,
                    "short",  // меньше 8 символов
                    UserRole.DIRECTOR,
                    null
            );

            // When & Then
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).register(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("Ошибка валидации - пустое имя, возвращает 400")
        void register_WithEmptyFirstName_ShouldReturnBadRequest() throws Exception {
            // Given
            RegisterRequest request = new RegisterRequest(
                    "test@test.com",
                    "",
                    "User",
                    null,
                    "password123",
                    UserRole.DIRECTOR,
                    null
            );

            // When & Then
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/login - Вход в систему")
    class LoginTests {

        @Test
        @DisplayName("Успешный вход - возвращает токены")
        void login_WithValidCredentials_ShouldReturnTokens() throws Exception {
            // Given
            LoginRequest loginRequest = new LoginRequest("login@test.com", "password123");

            AuthResponse expectedResponse = AuthResponse.of(
                    "login-access-token",
                    "login-refresh-token",
                    900L
            );

            when(userService.login(any(LoginRequest.class), nullable(String.class), nullable(String.class)))
                    .thenReturn(expectedResponse);

            // When & Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("login-access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("login-refresh-token"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"));

            verify(userService).login(any(LoginRequest.class), nullable(String.class), nullable(String.class));
        }

        @Test
        @DisplayName("Некорректный email - возвращает 400")
        void login_WithInvalidEmailFormat_ShouldReturnBadRequest() throws Exception {
            // Given
            LoginRequest loginRequest = new LoginRequest("not-an-email", "password123");

            // When & Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).login(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("Пустой пароль - возвращает 400")
        void login_WithEmptyPassword_ShouldReturnBadRequest() throws Exception {
            // Given
            LoginRequest loginRequest = new LoginRequest("test@test.com", "");

            // When & Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/auth/me - Получение текущего пользователя")
    class GetCurrentUserTests {

        @Test
        @DisplayName("Валидный токен - возвращает информацию о пользователе")
        void getCurrentUser_WithValidToken_ShouldReturnUserInfo() throws Exception {
            // Given
            UUID userId = UUID.randomUUID();
            String token = "valid-jwt-token";

            UserResponse expectedResponse = new UserResponse(
                    userId,
                    "me@test.com",
                    "Иванов Иван Иванович",
                    UserRole.DIRECTOR,
                    null,
                    null,
                    null
            );

            when(jwtTokenService.getUserIdFromToken(anyString())).thenReturn(userId);
            when(userService.getUserInfo(userId)).thenReturn(expectedResponse);

            // When & Then
            mockMvc.perform(get(ME_URL)
                            .header("Authorization", "Bearer " + token))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("me@test.com"))
                    .andExpect(jsonPath("$.role").value("DIRECTOR"))
                    .andExpect(jsonPath("$.fullName").value("Иванов Иван Иванович"));
        }

        @Test
        @DisplayName("Без токена - возвращает 500 (необработанное исключение)")
        void getCurrentUser_WithoutToken_ShouldReturnInternalServerError() throws Exception {
            mockMvc.perform(get(ME_URL))
                    .andExpect(status().isInternalServerError());
        }
    }

    @Nested
    @DisplayName("Тестирование JSON сериализации")
    class JsonSerializationTests {

        @Test
        @DisplayName("AuthResponse содержит все необходимые поля")
        void authResponse_ShouldContainAllFields() throws Exception {
            // Given
            RegisterRequest request = new RegisterRequest(
                    "json@test.com",
                    "Test",
                    "User",
                    null,
                    "password123",
                    UserRole.DIRECTOR,
                    null
            );

            AuthResponse expectedResponse = new AuthResponse(
                    "access-token",
                    "refresh-token",
                    3600L,
                    "Bearer"
            );

            when(userService.register(any(), nullable(String.class), nullable(String.class))).thenReturn(expectedResponse);

            // When & Then
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken").exists())
                    .andExpect(jsonPath("$.refreshToken").exists())
                    .andExpect(jsonPath("$.expiresIn").exists())
                    .andExpect(jsonPath("$.tokenType").exists());
        }
    }
}

