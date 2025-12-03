package by.bsuir.ssoservice.controller;

import by.bsuir.ssoservice.dto.request.LoginRequest;
import by.bsuir.ssoservice.dto.request.RefreshTokenRequest;
import by.bsuir.ssoservice.dto.request.RegisterRequest;
import by.bsuir.ssoservice.dto.response.AuthResponse;
import by.bsuir.ssoservice.dto.response.UserResponse;
import by.bsuir.ssoservice.model.enums.UserRole;
import by.bsuir.ssoservice.service.JwtTokenService;
import by.bsuir.ssoservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private AuthController authController;

    private AuthResponse authResponse;

    @BeforeEach
    void setUp() {
        authResponse = AuthResponse.of("access", "refresh", 3600L);
    }

    @Test
    void register_ShouldReturnCreatedResponse() {
        RegisterRequest request = new RegisterRequest("test@example.com", "First", "Last", null, "password", UserRole.DIRECTOR, null);
        when(userService.register(eq(request), anyString(), anyString())).thenReturn(authResponse);
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("Mozilla");

        ResponseEntity<AuthResponse> response = authController.register(request, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(authResponse);
    }

    @Test
    void login_ShouldReturnOkResponse() {
        LoginRequest request = new LoginRequest("test@example.com", "password");
        when(userService.login(eq(request), anyString(), anyString())).thenReturn(authResponse);
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("Mozilla");

        ResponseEntity<AuthResponse> response = authController.login(request, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(authResponse);
    }

    @Test
    void refresh_ShouldReturnNewTokens() {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");
        when(userService.refreshToken("refresh-token")).thenReturn(authResponse);

        ResponseEntity<AuthResponse> response = authController.refresh(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(authResponse);
    }

    @Test
    void logout_ShouldInvalidateToken() {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");

        ResponseEntity<Map<String, String>> response = authController.logout(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("message", "Успешный выход");
        verify(userService).logout("refresh-token");
    }

    @Test
    void getCurrentUser_ShouldReturnUserInfo() {
        UserResponse userResponse = new UserResponse(UUID.randomUUID(), "test@example.com", "Test User", UserRole.DIRECTOR, null, null, null);
        when(jwtTokenService.getUserIdFromToken("access-token")).thenReturn(userResponse.userId());
        when(userService.getUserInfo(userResponse.userId())).thenReturn(userResponse);

        ResponseEntity<UserResponse> response = authController.getCurrentUser("Bearer access-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(userResponse);
    }
}
