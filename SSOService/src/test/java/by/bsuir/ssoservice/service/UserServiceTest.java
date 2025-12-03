package by.bsuir.ssoservice.service;

import by.bsuir.ssoservice.dto.request.LoginRequest;
import by.bsuir.ssoservice.dto.request.RegisterRequest;
import by.bsuir.ssoservice.dto.response.AuthResponse;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock
    private UserEventRepository eventRepository;

    @Mock
    private UserReadModelRepository readModelRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private LoginAuditRepository loginAuditRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("register: Given valid DIRECTOR request When registering Then should create user and return auth response")
    void register_GivenValidDirectorRequest_WhenRegistering_ThenShouldCreateUserAndReturnAuthResponse() {
        RegisterRequest request = new RegisterRequest(
                "test@example.com",
                "John",
                "Doe",
                null,
                "password123",
                UserRole.DIRECTOR,
                null
        );

        String encodedPassword = "encodedPassword123";
        String accessToken = "accessToken";
        String refreshToken = "refreshToken";

        when(readModelRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn(encodedPassword);
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(eventRepository.save(any(UserEvent.class))).thenReturn(null);
        when(readModelRepository.save(any(UserReadModel.class))).thenAnswer(invocation -> {
            UserReadModel model = invocation.getArgument(0);
            model.setUserId(UUID.randomUUID());
            return model;
        });
        when(jwtTokenService.generateAccessToken(any(UUID.class), anyString(), any(UserRole.class)))
                .thenReturn(accessToken);
        when(jwtTokenService.generateRefreshToken()).thenReturn(refreshToken);
        doNothing().when(refreshTokenService).saveRefreshToken(anyString(), any(UUID.class), any(Duration.class));
        when(loginAuditRepository.save(any(LoginAudit.class))).thenReturn(null);

        AuthResponse response = userService.register(request, "127.0.0.1", "Mozilla");

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo(accessToken);
        assertThat(response.refreshToken()).isEqualTo(refreshToken);

        verify(readModelRepository).existsByEmail("test@example.com");
        verify(passwordEncoder).encode("password123");
        verify(eventRepository).save(any(UserEvent.class));
        verify(readModelRepository).save(any(UserReadModel.class));
        verify(jwtTokenService).generateAccessToken(any(UUID.class), eq("test@example.com"), eq(UserRole.DIRECTOR));
        verify(refreshTokenService).saveRefreshToken(eq(refreshToken), any(UUID.class), any(Duration.class));
    }

    @Test
    @DisplayName("register: Given duplicate email When registering Then should throw conflict exception")
    void register_GivenDuplicateEmail_WhenRegistering_ThenShouldThrowConflictException() {
        RegisterRequest request = new RegisterRequest(
                "existing@example.com",
                "John",
                "Doe",
                null,
                "password123",
                UserRole.DIRECTOR,
                null
        );

        when(readModelRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request, "127.0.0.1", "Mozilla"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("email уже существует");

        verify(readModelRepository).existsByEmail("existing@example.com");
        verify(passwordEncoder, never()).encode(anyString());
        verify(readModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("register: Given WORKER without organization code When registering Then should throw bad request exception")
    void register_GivenWorkerWithoutOrgCode_WhenRegistering_ThenShouldThrowBadRequestException() {
        RegisterRequest request = new RegisterRequest(
                "worker@example.com",
                "Jane",
                "Smith",
                null,
                "password123",
                UserRole.WORKER,
                null
        );

        when(readModelRepository.existsByEmail(anyString())).thenReturn(false);

        assertThatThrownBy(() -> userService.register(request, "127.0.0.1", "Mozilla"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("код предприятия");

        verify(readModelRepository).existsByEmail("worker@example.com");
        verify(readModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("login: Given valid credentials When logging in Then should return auth response")
    void login_GivenValidCredentials_WhenLoggingIn_ThenShouldReturnAuthResponse() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        UUID userId = UUID.randomUUID();
        String encodedPassword = "encodedPassword123";
        String accessToken = "accessToken";
        String refreshToken = "refreshToken";

        UserReadModel user = UserReadModel.builder()
                .userId(userId)
                .email("test@example.com")
                .fullName("John Doe")
                .role(UserRole.DIRECTOR)
                .passwordHash(encodedPassword)
                .provider(AuthProvider.LOCAL)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(readModelRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", encodedPassword)).thenReturn(true);
        when(jwtTokenService.generateAccessToken(userId, "test@example.com", UserRole.DIRECTOR))
                .thenReturn(accessToken);
        when(jwtTokenService.generateRefreshToken()).thenReturn(refreshToken);
        doNothing().when(refreshTokenService).saveRefreshToken(anyString(), any(UUID.class), any(Duration.class));
        when(loginAuditRepository.save(any(LoginAudit.class))).thenReturn(null);

        AuthResponse response = userService.login(request, "127.0.0.1", "Mozilla");

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo(accessToken);
        assertThat(response.refreshToken()).isEqualTo(refreshToken);

        verify(readModelRepository).findByEmail("test@example.com");
        verify(passwordEncoder).matches("password123", encodedPassword);
        verify(jwtTokenService).generateAccessToken(userId, "test@example.com", UserRole.DIRECTOR);
        verify(refreshTokenService).saveRefreshToken(eq(refreshToken), eq(userId), any(Duration.class));
        verify(loginAuditRepository).save(any(LoginAudit.class));
    }

    @Test
    @DisplayName("login: Given invalid email When logging in Then should throw unauthorized exception")
    void login_GivenInvalidEmail_WhenLoggingIn_ThenShouldThrowUnauthorizedException() {
        LoginRequest request = new LoginRequest("invalid@example.com", "password123");

        when(readModelRepository.findByEmail("invalid@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(request, "127.0.0.1", "Mozilla"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Неверный email или пароль");

        verify(readModelRepository).findByEmail("invalid@example.com");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtTokenService, never()).generateAccessToken(any(), anyString(), any());
    }

    @Test
    @DisplayName("login: Given invalid password When logging in Then should throw unauthorized exception")
    void login_GivenInvalidPassword_WhenLoggingIn_ThenShouldThrowUnauthorizedException() {
        LoginRequest request = new LoginRequest("test@example.com", "wrongPassword");
        UserReadModel user = UserReadModel.builder()
                .userId(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("encodedPassword123")
                .role(UserRole.DIRECTOR)
                .isActive(true)
                .provider(AuthProvider.LOCAL)
                .build();

        when(readModelRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword123")).thenReturn(false);

        assertThatThrownBy(() -> userService.login(request, "127.0.0.1", "Mozilla"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Неверный email или пароль");

        verify(readModelRepository).findByEmail("test@example.com");
        verify(passwordEncoder).matches("wrongPassword", "encodedPassword123");
        verify(jwtTokenService, never()).generateAccessToken(any(), anyString(), any());
    }

    @Test
    @DisplayName("login: Given inactive user When logging in Then should throw forbidden exception")
    void login_GivenInactiveUser_WhenLoggingIn_ThenShouldThrowForbiddenException() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        UserReadModel user = UserReadModel.builder()
                .userId(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("encodedPassword123")
                .role(UserRole.DIRECTOR)
                .isActive(false)
                .provider(AuthProvider.LOCAL)
                .build();

        when(readModelRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.login(request, "127.0.0.1", "Mozilla"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Аккаунт деактивирован");

        verify(readModelRepository).findByEmail("test@example.com");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtTokenService, never()).generateAccessToken(any(), anyString(), any());
    }

    @Test
    @DisplayName("getUserInfo: Given existing user ID When getting user info Then should return user response")
    void getUserInfo_GivenExistingUserId_WhenGettingUserInfo_ThenShouldReturnUserResponse() {
        UUID userId = UUID.randomUUID();
        UserReadModel user = UserReadModel.builder()
                .userId(userId)
                .email("test@example.com")
                .fullName("John Doe")
                .role(UserRole.DIRECTOR)
                .provider(AuthProvider.LOCAL)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(readModelRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserInfo(userId);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.fullName()).isEqualTo("John Doe");
        assertThat(response.role()).isEqualTo(UserRole.DIRECTOR);

        verify(readModelRepository).findById(userId);
    }

    @Test
    @DisplayName("getUserInfo: Given non-existing user ID When getting user info Then should throw not found exception")
    void getUserInfo_GivenNonExistingUserId_WhenGettingUserInfo_ThenShouldThrowNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(readModelRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserInfo(userId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Пользователь не найден");

        verify(readModelRepository).findById(userId);
    }

    @Test
    @DisplayName("refreshToken: Given valid refresh token Should return new tokens")
    void refreshToken_GivenValidRefreshToken_ShouldReturnNewTokens() {
        String refreshToken = "valid-refresh-token";
        UUID userId = UUID.randomUUID();
        String accessToken = "new-access-token";
        String newRefreshToken = "new-refresh-token";

        UserReadModel user = UserReadModel.builder()
                .userId(userId)
                .email("test@example.com")
                .fullName("John Doe")
                .role(UserRole.DIRECTOR)
                .provider(AuthProvider.LOCAL)
                .isActive(true)
                .build();

        when(refreshTokenService.getUserIdByRefreshToken(refreshToken)).thenReturn(userId);
        when(readModelRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtTokenService.generateAccessToken(userId, "test@example.com", UserRole.DIRECTOR))
                .thenReturn(accessToken);
        when(jwtTokenService.generateRefreshToken()).thenReturn(newRefreshToken);
        doNothing().when(refreshTokenService).deleteRefreshToken(refreshToken);

        AuthResponse response = userService.refreshToken(refreshToken);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo(accessToken);
        assertThat(response.refreshToken()).isEqualTo(newRefreshToken);

        verify(refreshTokenService).getUserIdByRefreshToken(refreshToken);
        verify(refreshTokenService).deleteRefreshToken(refreshToken);
        verify(readModelRepository).findById(userId);
    }

    @Test
    @DisplayName("refreshToken: Given invalid refresh token Should throw unauthorized exception")
    void refreshToken_GivenInvalidRefreshToken_ShouldThrowUnauthorizedException() {
        String refreshToken = "invalid-refresh-token";

        when(refreshTokenService.getUserIdByRefreshToken(refreshToken)).thenReturn(null);

        assertThatThrownBy(() -> userService.refreshToken(refreshToken))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Недействительный refresh token");

        verify(refreshTokenService).getUserIdByRefreshToken(refreshToken);
        verify(readModelRepository, never()).findById(any());
    }

    @Test
    @DisplayName("refreshToken: Given inactive user Should throw forbidden exception")
    void refreshToken_GivenInactiveUser_ShouldThrowForbiddenException() {
        String refreshToken = "valid-refresh-token";
        UUID userId = UUID.randomUUID();

        UserReadModel user = UserReadModel.builder()
                .userId(userId)
                .email("test@example.com")
                .isActive(false)
                .provider(AuthProvider.LOCAL)
                .build();

        when(refreshTokenService.getUserIdByRefreshToken(refreshToken)).thenReturn(userId);
        when(readModelRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.refreshToken(refreshToken))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Аккаунт деактивирован");

        verify(refreshTokenService).getUserIdByRefreshToken(refreshToken);
        verify(readModelRepository).findById(userId);
    }

    @Test
    @DisplayName("logout: Given valid refresh token Should deactivate session and delete token")
    void logout_GivenValidRefreshToken_ShouldDeactivateSessionAndDeleteToken() {
        String refreshToken = "valid-refresh-token";
        UUID userId = UUID.randomUUID();
        String hashedToken = "hashed-token";

        LoginAudit session = LoginAudit.builder()
                .id(1)
                .userId(userId)
                .refreshTokenHash(hashedToken)
                .isActive(true)
                .build();

        when(refreshTokenService.getUserIdByRefreshToken(refreshToken)).thenReturn(userId);
        when(loginAuditRepository.findByUserIdAndIsActiveTrue(userId)).thenReturn(java.util.List.of(session));
        when(passwordEncoder.matches(refreshToken, hashedToken)).thenReturn(true);

        userService.logout(refreshToken);

        verify(refreshTokenService).getUserIdByRefreshToken(refreshToken);
        verify(loginAuditRepository).findByUserIdAndIsActiveTrue(userId);
        verify(loginAuditRepository).save(any(LoginAudit.class));
        verify(refreshTokenService).deleteRefreshToken(refreshToken);
    }

    @Test
    @DisplayName("logout: Given null refresh token Should throw bad request exception")
    void logout_GivenNullRefreshToken_ShouldThrowBadRequestException() {
        assertThatThrownBy(() -> userService.logout(null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Refresh token обязателен");

        verify(refreshTokenService, never()).getUserIdByRefreshToken(any());
    }

    @Test
    @DisplayName("logoutAll: Should deactivate all sessions and delete all tokens")
    void logoutAll_ShouldDeactivateAllSessionsAndDeleteAllTokens() {
        UUID userId = UUID.randomUUID();

        userService.logoutAll(userId);

        verify(loginAuditRepository).deactivateAllUserSessions(userId);
        verify(refreshTokenService).deleteAllUserTokens(userId);
    }

    @Test
    @DisplayName("loginOAuthUser: Given active user Should return auth response")
    void loginOAuthUser_GivenActiveUser_ShouldReturnAuthResponse() {
        UUID userId = UUID.randomUUID();
        String accessToken = "access-token";
        String refreshToken = "refresh-token";

        UserReadModel user = UserReadModel.builder()
                .userId(userId)
                .email("test@example.com")
                .fullName("John Doe")
                .role(UserRole.DIRECTOR)
                .provider(AuthProvider.GOOGLE)
                .isActive(true)
                .build();

        when(jwtTokenService.generateAccessToken(userId, "test@example.com", UserRole.DIRECTOR))
                .thenReturn(accessToken);
        when(jwtTokenService.generateRefreshToken()).thenReturn(refreshToken);
        when(loginAuditRepository.save(any(LoginAudit.class))).thenReturn(null);

        AuthResponse response = userService.loginOAuthUser(user, "127.0.0.1", "Mozilla");

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo(accessToken);
        assertThat(response.refreshToken()).isEqualTo(refreshToken);

        verify(jwtTokenService).generateAccessToken(userId, "test@example.com", UserRole.DIRECTOR);
        verify(loginAuditRepository).save(any(LoginAudit.class));
    }

    @Test
    @DisplayName("loginOAuthUser: Given inactive user Should throw forbidden exception")
    void loginOAuthUser_GivenInactiveUser_ShouldThrowForbiddenException() {
        UserReadModel user = UserReadModel.builder()
                .userId(UUID.randomUUID())
                .email("test@example.com")
                .isActive(false)
                .provider(AuthProvider.GOOGLE)
                .build();

        assertThatThrownBy(() -> userService.loginOAuthUser(user, "127.0.0.1", "Mozilla"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Аккаунт деактивирован");

        verify(jwtTokenService, never()).generateAccessToken(any(), anyString(), any());
    }

    @Test
    @DisplayName("createOAuthUser: Given valid data Should create user")
    void createOAuthUser_GivenValidData_ShouldCreateUser() {
        String email = "oauth@example.com";
        String fullName = "OAuth User";
        String provider = "google";
        UserRole role = UserRole.DIRECTOR;

        when(readModelRepository.existsByEmail(email)).thenReturn(false);
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(eventRepository.save(any(UserEvent.class))).thenReturn(null);
        when(readModelRepository.save(any(UserReadModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserReadModel result = userService.createOAuthUser(email, fullName, provider, null, role, null, null);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getFullName()).isEqualTo(fullName);
        assertThat(result.getRole()).isEqualTo(role);
        assertThat(result.getProvider()).isEqualTo(AuthProvider.GOOGLE);

        verify(readModelRepository).existsByEmail(email);
        verify(eventRepository).save(any(UserEvent.class));
        verify(readModelRepository).save(any(UserReadModel.class));
    }

    @Test
    @DisplayName("createOAuthUser: Given existing email Should throw conflict exception")
    void createOAuthUser_GivenExistingEmail_ShouldThrowConflictException() {
        String email = "existing@example.com";

        when(readModelRepository.existsByEmail(email)).thenReturn(true);

        assertThatThrownBy(() -> userService.createOAuthUser(email, "Name", "google", null, UserRole.DIRECTOR, null, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("email уже существует");

        verify(readModelRepository).existsByEmail(email);
        verify(readModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("register: Given WORKER with organization code Should create user with organization and warehouse")
    void register_GivenWorkerWithOrgCode_ShouldCreateUserWithOrgAndWarehouse() {
        RegisterRequest request = new RegisterRequest(
                "worker@example.com",
                "Jane",
                "Worker",
                null,
                "password123",
                UserRole.WORKER,
                "ORG-CODE"
        );

        when(readModelRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(eventRepository.save(any(UserEvent.class))).thenReturn(null);
        when(readModelRepository.save(any(UserReadModel.class))).thenAnswer(invocation -> {
            UserReadModel model = invocation.getArgument(0);
            model.setUserId(UUID.randomUUID());
            return model;
        });
        when(jwtTokenService.generateAccessToken(any(UUID.class), anyString(), any(UserRole.class)))
                .thenReturn("accessToken");
        when(jwtTokenService.generateRefreshToken()).thenReturn("refreshToken");
        when(loginAuditRepository.save(any(LoginAudit.class))).thenReturn(null);

        AuthResponse response = userService.register(request, "127.0.0.1", "Mozilla");

        assertThat(response).isNotNull();

        verify(readModelRepository).save(any(UserReadModel.class));
    }

    @Test
    @DisplayName("register: Given ACCOUNTANT without organization code Should throw bad request exception")
    void register_GivenAccountantWithoutOrgCode_ShouldThrowBadRequestException() {
        RegisterRequest request = new RegisterRequest(
                "accountant@example.com",
                "Jane",
                "Accountant",
                null,
                "password123",
                UserRole.ACCOUNTANT,
                null
        );

        when(readModelRepository.existsByEmail(anyString())).thenReturn(false);

        assertThatThrownBy(() -> userService.register(request, "127.0.0.1", "Mozilla"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("код предприятия");
    }

    @Test
    @DisplayName("register: Given ACCOUNTANT with organization code Should create user with organization")
    void register_GivenAccountantWithOrgCode_ShouldCreateUserWithOrg() {
        RegisterRequest request = new RegisterRequest(
                "accountant@example.com",
                "Jane",
                "Accountant",
                null,
                "password123",
                UserRole.ACCOUNTANT,
                "ORG-CODE"
        );

        when(readModelRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(eventRepository.save(any(UserEvent.class))).thenReturn(null);
        when(readModelRepository.save(any(UserReadModel.class))).thenAnswer(invocation -> {
            UserReadModel model = invocation.getArgument(0);
            model.setUserId(UUID.randomUUID());
            return model;
        });
        when(jwtTokenService.generateAccessToken(any(UUID.class), anyString(), any(UserRole.class)))
                .thenReturn("accessToken");
        when(jwtTokenService.generateRefreshToken()).thenReturn("refreshToken");
        when(loginAuditRepository.save(any(LoginAudit.class))).thenReturn(null);

        AuthResponse response = userService.register(request, "127.0.0.1", "Mozilla");

        assertThat(response).isNotNull();
        verify(readModelRepository).save(any(UserReadModel.class));
    }

    @Test
    @DisplayName("logout: Given non-existing refresh token Should still delete from Redis")
    void logout_GivenNonExistingRefreshToken_ShouldStillDeleteFromRedis() {
        String refreshToken = "non-existing-token";

        when(refreshTokenService.getUserIdByRefreshToken(refreshToken)).thenReturn(null);

        userService.logout(refreshToken);

        verify(refreshTokenService).getUserIdByRefreshToken(refreshToken);
        verify(refreshTokenService).deleteRefreshToken(refreshToken);
        verify(loginAuditRepository, never()).findByUserIdAndIsActiveTrue(any());
    }

    @Test
    @DisplayName("logout: Given blank refresh token Should throw bad request exception")
    void logout_GivenBlankRefreshToken_ShouldThrowBadRequestException() {
        assertThatThrownBy(() -> userService.logout(""))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Refresh token обязателен");

        verify(refreshTokenService, never()).getUserIdByRefreshToken(any());
    }

    @Test
    @DisplayName("refreshToken: Given user not found Should throw not found exception")
    void refreshToken_GivenUserNotFound_ShouldThrowNotFoundException() {
        String refreshToken = "valid-refresh-token";
        UUID userId = UUID.randomUUID();

        when(refreshTokenService.getUserIdByRefreshToken(refreshToken)).thenReturn(userId);
        when(readModelRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.refreshToken(refreshToken))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Пользователь не найден");

        verify(refreshTokenService).getUserIdByRefreshToken(refreshToken);
        verify(readModelRepository).findById(userId);
    }

    @Test
    @DisplayName("createOAuthUser: Given WORKER role with warehouse Should create user with warehouse")
    void createOAuthUser_GivenWorkerRoleWithWarehouse_ShouldCreateUserWithWarehouse() {
        String email = "worker@example.com";
        String fullName = "Worker User";
        String provider = "google";
        UserRole role = UserRole.WORKER;
        String orgCode = UUID.randomUUID().toString();
        String whCode = UUID.randomUUID().toString();

        when(readModelRepository.existsByEmail(email)).thenReturn(false);
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(eventRepository.save(any(UserEvent.class))).thenReturn(null);
        when(readModelRepository.save(any(UserReadModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserReadModel result = userService.createOAuthUser(email, fullName, provider, null, role, orgCode, whCode);

        assertThat(result).isNotNull();
        assertThat(result.getOrganizationId()).isNotNull();
        assertThat(result.getWarehouseId()).isNotNull();

        verify(readModelRepository).save(any(UserReadModel.class));
    }

    @Test
    @DisplayName("getUserInfo: Given user with photo Should return photo in response")
    void getUserInfo_GivenUserWithPhoto_ShouldReturnPhotoInResponse() {
        UUID userId = UUID.randomUUID();
        byte[] photoBytes = "test-photo".getBytes();

        UserReadModel user = UserReadModel.builder()
                .userId(userId)
                .email("test@example.com")
                .fullName("John Doe")
                .role(UserRole.DIRECTOR)
                .provider(AuthProvider.LOCAL)
                .photo(photoBytes)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(readModelRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserInfo(userId);

        assertThat(response).isNotNull();
        assertThat(response.photoBase64()).isNotNull();
        assertThat(response.photoBase64()).isNotEmpty();

        verify(readModelRepository).findById(userId);
    }

    @Test
    @DisplayName("register: Given valid DIRECTOR request without IP and UserAgent Should create user")
    void register_GivenValidDirectorWithoutIpAndUserAgent_ShouldCreateUser() {
        RegisterRequest request = new RegisterRequest(
                "test@example.com",
                "John",
                "Doe",
                null,
                "password123",
                UserRole.DIRECTOR,
                null
        );

        when(readModelRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(eventRepository.save(any(UserEvent.class))).thenReturn(null);
        when(readModelRepository.save(any(UserReadModel.class))).thenAnswer(invocation -> {
            UserReadModel model = invocation.getArgument(0);
            model.setUserId(UUID.randomUUID());
            return model;
        });
        when(jwtTokenService.generateAccessToken(any(UUID.class), anyString(), any(UserRole.class)))
                .thenReturn("accessToken");
        when(jwtTokenService.generateRefreshToken()).thenReturn("refreshToken");
        when(loginAuditRepository.save(any(LoginAudit.class))).thenReturn(null);

        AuthResponse response = userService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("accessToken");

        verify(readModelRepository).save(any(UserReadModel.class));
        verify(loginAuditRepository).save(any(LoginAudit.class));
    }
}
