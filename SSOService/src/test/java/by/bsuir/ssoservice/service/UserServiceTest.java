package by.bsuir.ssoservice.service;

import by.bsuir.ssoservice.dto.request.LoginRequest;
import by.bsuir.ssoservice.dto.request.RegisterDirectorRequest;
import by.bsuir.ssoservice.dto.request.RegisterWithInvitationRequest;
import by.bsuir.ssoservice.dto.response.AuthResponse;
import by.bsuir.ssoservice.dto.response.InvitationValidationResponse;
import by.bsuir.ssoservice.dto.response.UserResponse;
import by.bsuir.ssoservice.exception.AppException;
import by.bsuir.ssoservice.model.entity.LoginAudit;
import by.bsuir.ssoservice.model.entity.UserEvent;
import by.bsuir.ssoservice.model.entity.UserReadModel;
import by.bsuir.ssoservice.model.enums.AuthProvider;
import by.bsuir.ssoservice.model.enums.UserRole;
import by.bsuir.ssoservice.repository.LoginAuditRepository;
import by.bsuir.ssoservice.repository.UserEventRepository;
import by.bsuir.ssoservice.repository.UserReadModelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService — модульные тесты")
class UserServiceTest {

    @Mock private UserEventRepository eventRepository;
    @Mock private UserReadModelRepository readModelRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenService jwtTokenService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private LoginAuditRepository loginAuditRepository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private InvitationValidationService invitationValidationService;

    @InjectMocks private UserService userService;

    @Test
    @DisplayName("registerDirector: новый email → создаёт DIRECTOR-пользователя и audit-запись")
    void registerDirector_GivenNewEmail_ShouldCreateAndAudit() {
        RegisterDirectorRequest req = new RegisterDirectorRequest(
                "boss@example.com", "P@ssword123", "Александр", "Сидоров", "Петрович");
        when(readModelRepository.existsByEmail("boss@example.com")).thenReturn(false);
        when(passwordEncoder.encode("P@ssword123")).thenReturn("hash");
        when(jwtTokenService.generateAccessToken(any(), eq("boss@example.com"), eq(UserRole.DIRECTOR), any(), any()))
                .thenReturn("access-jwt");
        when(jwtTokenService.generateRefreshToken()).thenReturn("refresh-uuid");
        when(jwtTokenService.getAccessTokenValidity()).thenReturn(14400L);
        when(jwtTokenService.getRefreshTokenValidity()).thenReturn(2592000L);
        when(passwordEncoder.encode("refresh-uuid")).thenReturn("refresh-hash");

        AuthResponse response = userService.registerDirector(req, "127.0.0.1", "Mozilla");

        assertThat(response.accessToken()).isEqualTo("access-jwt");
        assertThat(response.refreshToken()).isEqualTo("refresh-uuid");

        ArgumentCaptor<UserReadModel> rmCaptor = ArgumentCaptor.forClass(UserReadModel.class);
        verify(readModelRepository).save(rmCaptor.capture());
        UserReadModel saved = rmCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("boss@example.com");
        assertThat(saved.getRole()).isEqualTo(UserRole.DIRECTOR);
        assertThat(saved.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(saved.getFullName()).isEqualTo("Сидоров Александр Петрович");
        assertThat(saved.getOrganizationId()).isNull();

        verify(eventRepository).save(any(UserEvent.class));
        verify(refreshTokenService).saveRefreshToken(eq("refresh-uuid"), any(UUID.class), any(Duration.class));
        verify(loginAuditRepository).save(any(LoginAudit.class));
    }

    @Test
    @DisplayName("registerDirector: дубликат email → 409 conflict")
    void registerDirector_GivenDuplicateEmail_ShouldThrowConflict() {
        RegisterDirectorRequest req = new RegisterDirectorRequest(
                "boss@example.com", "P@ssword123", "A", "S", null);
        when(readModelRepository.existsByEmail("boss@example.com")).thenReturn(true);

        AppException ex = catchApp(() -> userService.registerDirector(req, "ip", "ua"));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(readModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("registerWithInvitation: валидное приглашение + совпадающий email → создаёт пользователя в орг и помечает invite использованным")
    void registerWithInvitation_GivenValid_ShouldCreateAndMarkUsed() {
        UUID token = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        RegisterWithInvitationRequest req = new RegisterWithInvitationRequest(
                token, "worker@example.com", "P@ssword123", "Иван", "Петров", null);

        when(invitationValidationService.validateInvitation(token)).thenReturn(
                new InvitationValidationResponse(true, "ООО Ромашка", "WORKER",
                        "worker@example.com", orgId, warehouseId, "OK"));
        when(readModelRepository.existsByEmail("worker@example.com")).thenReturn(false);
        when(passwordEncoder.encode("P@ssword123")).thenReturn("hash");
        when(jwtTokenService.generateAccessToken(any(), eq("worker@example.com"),
                eq(UserRole.WORKER), eq(orgId), eq(warehouseId))).thenReturn("access");
        when(jwtTokenService.generateRefreshToken()).thenReturn("refresh");
        when(jwtTokenService.getAccessTokenValidity()).thenReturn(14400L);
        when(jwtTokenService.getRefreshTokenValidity()).thenReturn(2592000L);
        when(passwordEncoder.encode("refresh")).thenReturn("refresh-hash");

        AuthResponse response = userService.registerWithInvitation(req, "ip", "ua");

        assertThat(response.accessToken()).isEqualTo("access");

        ArgumentCaptor<UserReadModel> rmCaptor = ArgumentCaptor.forClass(UserReadModel.class);
        verify(readModelRepository).save(rmCaptor.capture());
        UserReadModel saved = rmCaptor.getValue();
        assertThat(saved.getRole()).isEqualTo(UserRole.WORKER);
        assertThat(saved.getOrganizationId()).isEqualTo(orgId);
        assertThat(saved.getWarehouseId()).isEqualTo(warehouseId);

        verify(invitationValidationService).addEmployeeToOrganization(orgId, saved.getUserId(), "WORKER");
        verify(invitationValidationService).markInvitationAsUsed(eq(token), eq(saved.getUserId()));
    }

    @Test
    @DisplayName("registerWithInvitation: invitation invalid → 400 bad request, без сохранения и markUsed")
    void registerWithInvitation_GivenInvalid_ShouldThrowBadRequest() {
        UUID token = UUID.randomUUID();
        when(invitationValidationService.validateInvitation(token)).thenReturn(
                new InvitationValidationResponse(false, null, null, null, null, null, "Истёк"));

        RegisterWithInvitationRequest req = new RegisterWithInvitationRequest(
                token, "x@y.com", "P@ssword123", "A", "B", null);

        AppException ex = catchApp(() -> userService.registerWithInvitation(req, "ip", "ua"));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(readModelRepository, never()).save(any());
        verify(invitationValidationService, never()).markInvitationAsUsed(any(), any());
    }

    @Test
    @DisplayName("registerWithInvitation: email пользователя не совпадает с email в приглашении → 400")
    void registerWithInvitation_GivenEmailMismatch_ShouldThrowBadRequest() {
        UUID token = UUID.randomUUID();
        when(invitationValidationService.validateInvitation(token)).thenReturn(
                new InvitationValidationResponse(true, "ООО Ромашка", "WORKER",
                        "real@invitation.com", UUID.randomUUID(), null, "OK"));

        RegisterWithInvitationRequest req = new RegisterWithInvitationRequest(
                token, "fraud@evil.com", "P@ssword123", "A", "B", null);

        AppException ex = catchApp(() -> userService.registerWithInvitation(req, "ip", "ua"));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("Email");
        verify(readModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("registerWithInvitation: дубликат email после валидного приглашения → 409 conflict")
    void registerWithInvitation_GivenDuplicateEmail_ShouldThrowConflict() {
        UUID token = UUID.randomUUID();
        when(invitationValidationService.validateInvitation(token)).thenReturn(
                new InvitationValidationResponse(true, "ООО Ромашка", "WORKER",
                        "worker@example.com", UUID.randomUUID(), null, "OK"));
        when(readModelRepository.existsByEmail("worker@example.com")).thenReturn(true);

        RegisterWithInvitationRequest req = new RegisterWithInvitationRequest(
                token, "worker@example.com", "P@ssword123", "A", "B", null);

        AppException ex = catchApp(() -> userService.registerWithInvitation(req, "ip", "ua"));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("registerWithInvitation: приглашение с ролью DIRECTOR → 400 bad request")
    void registerWithInvitation_GivenDirectorRole_ShouldThrowBadRequest() {
        UUID token = UUID.randomUUID();
        when(invitationValidationService.validateInvitation(token)).thenReturn(
                new InvitationValidationResponse(true, "ООО Ромашка", "DIRECTOR",
                        "x@y.com", UUID.randomUUID(), null, "OK"));
        when(readModelRepository.existsByEmail("x@y.com")).thenReturn(false);

        RegisterWithInvitationRequest req = new RegisterWithInvitationRequest(
                token, "x@y.com", "P@ssword123", "A", "B", null);

        AppException ex = catchApp(() -> userService.registerWithInvitation(req, "ip", "ua"));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("DIRECTOR");
    }

    @Test
    @DisplayName("login: валидные креды → выдаёт токены и audit")
    void login_GivenValidCredentials_ShouldReturnTokens() {
        UUID userId = UUID.randomUUID();
        UserReadModel user = sampleUser(userId, "ivan@example.com", UserRole.WORKER, "hash", true);
        when(readModelRepository.findByEmail("ivan@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("P@ssword123", "hash")).thenReturn(true);
        when(jwtTokenService.generateAccessToken(eq(userId), eq("ivan@example.com"), eq(UserRole.WORKER), any(), any()))
                .thenReturn("access");
        when(jwtTokenService.generateRefreshToken()).thenReturn("refresh");
        when(jwtTokenService.getAccessTokenValidity()).thenReturn(14400L);
        when(jwtTokenService.getRefreshTokenValidity()).thenReturn(2592000L);
        when(passwordEncoder.encode("refresh")).thenReturn("refresh-hash");

        AuthResponse response = userService.login(new LoginRequest("ivan@example.com", "P@ssword123"), "ip", "ua");

        assertThat(response.accessToken()).isEqualTo("access");
        verify(loginAuditRepository).save(any(LoginAudit.class));
    }

    @Test
    @DisplayName("login: неверный пароль → 401 unauthorized")
    void login_GivenWrongPassword_ShouldThrowUnauthorized() {
        UserReadModel user = sampleUser(UUID.randomUUID(), "x@y.z", UserRole.WORKER, "hash", true);
        when(readModelRepository.findByEmail("x@y.z")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        AppException ex = catchApp(() -> userService.login(new LoginRequest("x@y.z", "wrong"), "ip", "ua"));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(loginAuditRepository, never()).save(any());
    }

    @Test
    @DisplayName("login: пользователь не найден → 401 (не leak'ает существование email)")
    void login_GivenMissingUser_ShouldThrowUnauthorized() {
        when(readModelRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        AppException ex = catchApp(() -> userService.login(
                new LoginRequest("ghost@example.com", "any"), "ip", "ua"));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("login: деактивированный аккаунт → 403 forbidden")
    void login_GivenInactiveUser_ShouldThrowForbidden() {
        UserReadModel user = sampleUser(UUID.randomUUID(), "x@y.z", UserRole.WORKER, "hash", false);
        when(readModelRepository.findByEmail("x@y.z")).thenReturn(Optional.of(user));

        AppException ex = catchApp(() -> userService.login(
                new LoginRequest("x@y.z", "P@ssword123"), "ip", "ua"));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("refreshToken: валидный → выдаёт новые токены, старый удаляется")
    void refreshToken_GivenValid_ShouldRotate() {
        UUID userId = UUID.randomUUID();
        UserReadModel user = sampleUser(userId, "ivan@example.com", UserRole.WORKER, "hash", true);
        when(refreshTokenService.getUserIdByRefreshToken("old-refresh")).thenReturn(userId);
        when(readModelRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtTokenService.generateAccessToken(eq(userId), any(), any(), any(), any())).thenReturn("new-access");
        when(jwtTokenService.generateRefreshToken()).thenReturn("new-refresh");
        when(jwtTokenService.getAccessTokenValidity()).thenReturn(14400L);
        when(jwtTokenService.getRefreshTokenValidity()).thenReturn(2592000L);

        AuthResponse response = userService.refreshToken("old-refresh");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenService).deleteRefreshToken("old-refresh");
    }

    @Test
    @DisplayName("refreshToken: неизвестный refresh → 401")
    void refreshToken_GivenUnknownRefresh_ShouldThrowUnauthorized() {
        when(refreshTokenService.getUserIdByRefreshToken("bad")).thenReturn(null);

        AppException ex = catchApp(() -> userService.refreshToken("bad"));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("refreshToken: деактивированный аккаунт → 403, рефреш не удаляется (важно для аудита)")
    void refreshToken_GivenInactive_ShouldThrowForbidden() {
        UUID userId = UUID.randomUUID();
        UserReadModel user = sampleUser(userId, "x@y.z", UserRole.WORKER, "hash", false);
        when(refreshTokenService.getUserIdByRefreshToken("ok-refresh")).thenReturn(userId);
        when(readModelRepository.findById(userId)).thenReturn(Optional.of(user));

        AppException ex = catchApp(() -> userService.refreshToken("ok-refresh"));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(refreshTokenService, never()).deleteRefreshToken(anyString());
    }

    @Test
    @DisplayName("logout: валидный refresh → деактивирует session и удаляет токен")
    void logout_GivenValidRefresh_ShouldDeactivateSession() {
        UUID userId = UUID.randomUUID();
        when(refreshTokenService.getUserIdByRefreshToken("refresh-X")).thenReturn(userId);
        LoginAudit session = LoginAudit.builder()
                .userId(userId).refreshTokenHash("hash-of-refresh-X").isActive(true).build();
        when(loginAuditRepository.findByUserIdAndIsActiveTrue(userId)).thenReturn(List.of(session));
        when(passwordEncoder.matches("refresh-X", "hash-of-refresh-X")).thenReturn(true);

        userService.logout("refresh-X");

        assertThat(session.getIsActive()).isFalse();
        assertThat(session.getLogoutAt()).isNotNull();
        verify(loginAuditRepository).save(session);
        verify(refreshTokenService).deleteRefreshToken("refresh-X");
    }

    @Test
    @DisplayName("logout: refresh пуст → 400 bad request")
    void logout_GivenBlankRefresh_ShouldThrowBadRequest() {
        AppException ex = catchApp(() -> userService.logout(""));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("logoutAll: чистит сессии в audit и Redis")
    void logoutAll_ShouldDeactivateAllAndClearRedis() {
        UUID userId = UUID.randomUUID();
        userService.logoutAll(userId);
        verify(loginAuditRepository).deactivateAllUserSessions(userId);
        verify(refreshTokenService).deleteAllUserTokens(userId);
    }

    @Test
    @DisplayName("getUserInfo: существующий → возвращает UserResponse, photo сериализуется в Base64")
    void getUserInfo_GivenExistingUser_ShouldReturnResponseWithPhotoBase64() {
        UUID userId = UUID.randomUUID();
        UserReadModel user = sampleUser(userId, "x@y.z", UserRole.WORKER, "hash", true);
        user.setPhoto(new byte[]{1, 2, 3});
        when(readModelRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserInfo(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("x@y.z");
        assertThat(response.role()).isEqualTo(UserRole.WORKER);
        assertThat(response.photoBase64()).isEqualTo("AQID");
    }

    @Test
    @DisplayName("getUserInfo: нет пользователя → 404")
    void getUserInfo_GivenMissing_ShouldThrowNotFound() {
        UUID userId = UUID.randomUUID();
        when(readModelRepository.findById(userId)).thenReturn(Optional.empty());

        AppException ex = catchApp(() -> userService.getUserInfo(userId));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("createOAuthUser: новый email → создаёт без passwordHash, провайдер из аргумента")
    void createOAuthUser_GivenNewEmail_ShouldCreate() {
        UUID orgId = UUID.randomUUID();
        when(readModelRepository.existsByEmail("oa@example.com")).thenReturn(false);

        UserReadModel saved = userService.createOAuthUser(
                "oa@example.com", "Иван Петров", "google", null,
                UserRole.DIRECTOR, orgId.toString(), null);

        assertThat(saved.getEmail()).isEqualTo("oa@example.com");
        assertThat(saved.getRole()).isEqualTo(UserRole.DIRECTOR);
        assertThat(saved.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getOrganizationId()).isEqualTo(orgId);
        verify(readModelRepository).save(any(UserReadModel.class));
        verify(eventRepository).save(any(UserEvent.class));
    }

    @Test
    @DisplayName("createOAuthUser: дубликат email → 409 conflict")
    void createOAuthUser_GivenDuplicateEmail_ShouldThrowConflict() {
        when(readModelRepository.existsByEmail("oa@example.com")).thenReturn(true);

        AppException ex = catchApp(() -> userService.createOAuthUser(
                "oa@example.com", "X", "google", null, UserRole.WORKER, null, null));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    private UserReadModel sampleUser(UUID userId, String email, UserRole role, String hash, boolean active) {
        return UserReadModel.builder()
                .userId(userId)
                .email(email)
                .fullName("Test User")
                .role(role)
                .passwordHash(hash)
                .provider(AuthProvider.LOCAL)
                .isActive(active)
                .build();
    }

    private static AppException catchApp(Runnable r) {
        try {
            r.run();
        } catch (AppException e) {
            return e;
        }
        throw new AssertionError("Expected AppException");
    }
}
