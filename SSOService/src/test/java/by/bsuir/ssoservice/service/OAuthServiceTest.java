package by.bsuir.ssoservice.service;

import by.bsuir.ssoservice.dto.request.CompleteOAuthRegistrationRequest;
import by.bsuir.ssoservice.dto.response.AuthResponse;
import by.bsuir.ssoservice.dto.response.OAuthRegistrationResponse;
import by.bsuir.ssoservice.exception.AppException;
import by.bsuir.ssoservice.model.entity.OAuthPendingRegistration;
import by.bsuir.ssoservice.model.entity.UserReadModel;
import by.bsuir.ssoservice.model.enums.AuthProvider;
import by.bsuir.ssoservice.model.enums.UserRole;
import by.bsuir.ssoservice.repository.OAuthPendingRegistrationRepository;
import by.bsuir.ssoservice.repository.UserReadModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthServiceTest {

    @Mock
    private UserReadModelRepository userRepository;

    @Mock
    private OAuthPendingRegistrationRepository pendingRegistrationRepository;

    @Mock
    private UserService userService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private OAuthService oauthService;

    private UserReadModel existingUser;

    @BeforeEach
    void setUp() {
        existingUser = UserReadModel.builder()
                .userId(UUID.randomUUID())
                .email("test@example.com")
                .fullName("Test User")
                .role(UserRole.DIRECTOR)
                .provider(AuthProvider.GOOGLE)
                .build();
    }

    @Test
    void getAuthorizationUrl_GivenGoogleProvider_ShouldReturnGoogleUrl() {
        String url = oauthService.getAuthorizationUrl("google", "login");
        assertThat(url).contains("accounts.google.com");
    }

    @Test
    void getAuthorizationUrl_GivenUnsupportedProvider_ShouldThrowException() {
        assertThatThrownBy(() -> oauthService.getAuthorizationUrl("unknown", "login"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Неподдерживаемый провайдер");
    }

    @Test
    void handleCallback_WhenUserExists_ShouldLoginViaUserService() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "external-token")));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "id", "123",
                        "email", "test@example.com",
                        "name", "Test User"
                )));
        when(userRepository.findByEmailAndProvider(anyString(), any(AuthProvider.class))).thenReturn(Optional.of(existingUser));
        when(userService.loginOAuthUser(eq(existingUser), anyString(), anyString())).thenReturn(AuthResponse.of("access", "refresh", 3600L));

        Object result = oauthService.handleCallback("google", "code", "state", "127.0.0.1", "Mozilla");

        assertThat(result).isInstanceOf(AuthResponse.class);
        verify(userService).loginOAuthUser(eq(existingUser), anyString(), anyString());
    }

    @Test
    void handleCallback_WhenUserDoesNotExist_ShouldCreatePendingRegistration() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "external-token")));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "id", "123",
                        "email", "test@example.com",
                        "name", "Test User"
                )));
        when(userRepository.findByEmailAndProvider(anyString(), any(AuthProvider.class))).thenReturn(Optional.empty());
        when(pendingRegistrationRepository.findByEmailAndProviderAndCompletedFalse(anyString(), anyString())).thenReturn(Optional.empty());

        Object result = oauthService.handleCallback("google", "code", "state", "127.0.0.1", "Mozilla");

        assertThat(result).isInstanceOf(OAuthRegistrationResponse.class);
        verify(pendingRegistrationRepository).save(any(OAuthPendingRegistration.class));
    }

    @Test
    void completeRegistration_GivenValidToken_ShouldCreateUserAndGenerateTokens() {
        OAuthPendingRegistration pending = OAuthPendingRegistration.builder()
                .temporaryToken("temp-token")
                .email("new@example.com")
                .fullName("New User")
                .provider("google")
                .providerUid("123")
                .photo(null)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .completed(false)
                .build();

        CompleteOAuthRegistrationRequest request = new CompleteOAuthRegistrationRequest("temp-token", UserRole.WORKER, "org", "wh");

        when(pendingRegistrationRepository.findByTemporaryTokenAndCompletedFalse("temp-token"))
                .thenReturn(Optional.of(pending));
        when(userService.createOAuthUser(anyString(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(existingUser);
        when(userService.generateTokensWithAudit(eq(existingUser), anyString(), anyString()))
                .thenReturn(AuthResponse.of("access", "refresh", 3600L));

        AuthResponse response = oauthService.completeRegistration(request, "127.0.0.1", "Mozilla");

        assertThat(response.accessToken()).isEqualTo("access");
        verify(pendingRegistrationRepository).save(any(OAuthPendingRegistration.class));
    }

    @Test
    void completeRegistration_WithExpiredToken_ShouldThrowException() {
        OAuthPendingRegistration pending = OAuthPendingRegistration.builder()
                .temporaryToken("temp-token")
                .email("new@example.com")
                .fullName("New User")
                .provider("google")
                .providerUid("123")
                .photo(null)
                .createdAt(LocalDateTime.now().minusHours(1))
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .completed(false)
                .build();

        when(pendingRegistrationRepository.findByTemporaryTokenAndCompletedFalse("temp-token"))
                .thenReturn(Optional.of(pending));

        CompleteOAuthRegistrationRequest request = new CompleteOAuthRegistrationRequest("temp-token", UserRole.WORKER, "org", "wh");

        assertThatThrownBy(() -> oauthService.completeRegistration(request, "127.0.0.1", "Mozilla"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Токен регистрации истек");
    }

    @Test
    void cleanupExpiredRegistrations_ShouldTriggerRepositoryCleanup() {
        oauthService.cleanupExpiredRegistrations();
        verify(pendingRegistrationRepository).deleteExpiredOrCompleted(any(LocalDateTime.class));
    }

    @Test
    void getAuthorizationUrl_GivenYandexProvider_ShouldReturnYandexUrl() {
        String url = oauthService.getAuthorizationUrl("yandex", "login");
        assertThat(url).contains("oauth.yandex.ru");
        assertThat(url).contains("state=login");
    }

    @Test
    void completeRegistration_WithInvalidToken_ShouldThrowException() {
        CompleteOAuthRegistrationRequest request = new CompleteOAuthRegistrationRequest("invalid-token", UserRole.WORKER, "org", "wh");

        when(pendingRegistrationRepository.findByTemporaryTokenAndCompletedFalse("invalid-token"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> oauthService.completeRegistration(request, "127.0.0.1", "Mozilla"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Недействительный или истекший токен");
    }

@Test
    void handleCallback_WithRestTemplateException_ShouldThrowAppException() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Network error"));

        assertThatThrownBy(() -> oauthService.handleCallback("google", "code", "state", "127.0.0.1", "Mozilla"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Ошибка авторизации");
    }

    @Test
    void handleCallback_WhenNewUserRegistration_ShouldDeleteExistingPendingRegistration() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "external-token")));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "id", "123",
                        "email", "test@example.com",
                        "name", "Test User"
                )));
        when(userRepository.findByEmailAndProvider(anyString(), any(AuthProvider.class)))
                .thenReturn(Optional.empty());

        OAuthPendingRegistration existingPending = OAuthPendingRegistration.builder()
                .temporaryToken("old-token")
                .email("test@example.com")
                .provider("google")
                .build();

        when(pendingRegistrationRepository.findByEmailAndProviderAndCompletedFalse(anyString(), anyString()))
                .thenReturn(Optional.of(existingPending));

        Object result = oauthService.handleCallback("google", "code", "state", "127.0.0.1", "Mozilla");

        assertThat(result).isInstanceOf(OAuthRegistrationResponse.class);
        verify(pendingRegistrationRepository).delete(existingPending);
        verify(pendingRegistrationRepository).save(any(OAuthPendingRegistration.class));
    }

    @Test
    void completeRegistration_WithValidData_ShouldMarkPendingAsCompleted() {
        OAuthPendingRegistration pending = OAuthPendingRegistration.builder()
                .temporaryToken("temp-token")
                .email("new@example.com")
                .fullName("New User")
                .provider("google")
                .providerUid("123")
                .photo(null)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .completed(false)
                .build();

        CompleteOAuthRegistrationRequest request = new CompleteOAuthRegistrationRequest(
                "temp-token", UserRole.DIRECTOR, null, null);

        when(pendingRegistrationRepository.findByTemporaryTokenAndCompletedFalse("temp-token"))
                .thenReturn(Optional.of(pending));
        when(userService.createOAuthUser(anyString(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(existingUser);
        when(userService.generateTokensWithAudit(eq(existingUser), anyString(), anyString()))
                .thenReturn(AuthResponse.of("access", "refresh", 3600L));

        AuthResponse response = oauthService.completeRegistration(request, "127.0.0.1", "Mozilla");

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(pending.getCompleted()).isTrue();
        verify(pendingRegistrationRepository).save(pending);
    }
}
