package by.bsuir.ssoservice.service;

import by.bsuir.ssoservice.dto.request.ChangePasswordRequest;
import by.bsuir.ssoservice.dto.request.UpdateProfileRequest;
import by.bsuir.ssoservice.exception.AppException;
import by.bsuir.ssoservice.model.entity.LoginAudit;
import by.bsuir.ssoservice.model.entity.UserReadModel;
import by.bsuir.ssoservice.model.enums.UserRole;
import by.bsuir.ssoservice.repository.LoginAuditRepository;
import by.bsuir.ssoservice.repository.UserReadModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileService — модульные тесты")
class ProfileServiceTest {

    @Mock private UserReadModelRepository userRepository;
    @Mock private LoginAuditRepository loginAuditRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks private ProfileService service;

    private UserReadModel user(UUID id, String email, String passwordHash, byte[] photo) {
        UserReadModel u = new UserReadModel();
        u.setUserId(id);
        u.setEmail(email);
        u.setFullName("Test User");
        u.setRole(UserRole.WORKER);
        u.setPasswordHash(passwordHash);
        u.setPhoto(photo);
        u.setIsActive(true);
        return u;
    }

    @Test
    @DisplayName("getUserProfile: возвращает UserResponse с base64-фото если оно есть")
    void getUserProfile_GivenUserWithPhoto_ShouldReturnBase64() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user(id, "u@e.com", "hash", new byte[]{1, 2, 3})));

        var resp = service.getUserProfile(id);

        assertThat(resp.userId()).isEqualTo(id);
        assertThat(resp.email()).isEqualTo("u@e.com");
        assertThat(resp.photoBase64()).isNotNull();
    }

    @Test
    @DisplayName("getUserProfile: без фото → photoBase64 == null")
    void getUserProfile_GivenNoPhoto_ShouldReturnNullPhoto() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user(id, "u@e.com", "hash", null)));

        var resp = service.getUserProfile(id);
        assertThat(resp.photoBase64()).isNull();
    }

    @Test
    @DisplayName("updateProfile: смена email → проверяет currentPassword")
    void updateProfile_GivenEmailChange_ShouldRequireCurrentPassword() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user(id, "old@e.com", "hash", null)));
        UpdateProfileRequest req = new UpdateProfileRequest(
                "Иван", "Иванов", null, "new@e.com", null
        );

        assertThatThrownBy(() -> service.updateProfile(id, req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("текущий пароль");
    }

    @Test
    @DisplayName("updateProfile: смена email + неверный пароль → unauthorized")
    void updateProfile_GivenEmailChangeWrongPassword_ShouldThrowUnauthorized() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user(id, "old@e.com", "hash", null)));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        UpdateProfileRequest req = new UpdateProfileRequest(
                "Иван", "Иванов", null, "new@e.com", "wrong"
        );

        assertThatThrownBy(() -> service.updateProfile(id, req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("неверен");
    }

    @Test
    @DisplayName("updateProfile: OAuth-аккаунт (passwordHash=null) + смена email → bad request")
    void updateProfile_GivenOAuthAccountEmailChange_ShouldThrow() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user(id, "old@e.com", null, null)));
        UpdateProfileRequest req = new UpdateProfileRequest(
                "Иван", "Иванов", null, "new@e.com", null
        );

        assertThatThrownBy(() -> service.updateProfile(id, req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("OAuth");
    }

    @Test
    @DisplayName("updateProfile: тот же email → обновляет имя без проверки пароля")
    void updateProfile_GivenSameEmail_ShouldUpdateName() {
        UUID id = UUID.randomUUID();
        UserReadModel u = user(id, "u@e.com", "hash", null);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest(
                "Иван", "Петров", "Сергеевич", "u@e.com", null
        );

        var resp = service.updateProfile(id, req);

        assertThat(resp.fullName()).isEqualTo("Петров Иван Сергеевич");
        verify(userRepository).save(any());
    }

    @Test
    @DisplayName("uploadPhoto: > 5 МБ → исключение")
    void uploadPhoto_GivenTooLarge_ShouldThrow() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user(id, "u@e.com", "h", null)));
        var photo = new org.springframework.mock.web.MockMultipartFile(
                "photo", "p.jpg", "image/jpeg", new byte[6 * 1024 * 1024]);

        assertThatThrownBy(() -> service.uploadPhoto(id, photo))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("5 МБ");
    }

    @Test
    @DisplayName("uploadPhoto: не image/* → исключение")
    void uploadPhoto_GivenNonImage_ShouldThrow() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user(id, "u@e.com", "h", null)));
        var photo = new org.springframework.mock.web.MockMultipartFile(
                "photo", "p.txt", "text/plain", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.uploadPhoto(id, photo))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("изображением");
    }

    @Test
    @DisplayName("uploadPhoto: image/jpeg в норме → сохраняет байты и возвращает base64")
    void uploadPhoto_GivenValid_ShouldSaveAndReturnBase64() throws Exception {
        UUID id = UUID.randomUUID();
        UserReadModel u = user(id, "u@e.com", "h", null);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        byte[] data = new byte[]{10, 20, 30};
        var photo = new org.springframework.mock.web.MockMultipartFile(
                "photo", "p.jpg", "image/jpeg", data);

        String base64 = service.uploadPhoto(id, photo);

        assertThat(base64).isNotBlank();
        assertThat(u.getPhoto()).isEqualTo(data);
        verify(userRepository).save(u);
    }

    @Test
    @DisplayName("deletePhoto: с фото → setPhoto(null) + save")
    void deletePhoto_GivenUserWithPhoto_ShouldClear() {
        UUID id = UUID.randomUUID();
        UserReadModel u = user(id, "u@e.com", "h", new byte[]{1, 2});
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        service.deletePhoto(id);

        assertThat(u.getPhoto()).isNull();
        verify(userRepository).save(u);
    }

    @Test
    @DisplayName("deletePhoto: без фото → save не вызывается")
    void deletePhoto_GivenNoPhoto_ShouldNotSave() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user(id, "u@e.com", "h", null)));

        service.deletePhoto(id);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("changePassword: неверный текущий → unauthorized")
    void changePassword_GivenWrongCurrent_ShouldThrow() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user(id, "u@e.com", "hash", null)));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(id, new ChangePasswordRequest("wrong", "newpass")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("неверен");
    }

    @Test
    @DisplayName("changePassword: новый == старый → bad request")
    void changePassword_GivenSamePassword_ShouldThrow() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user(id, "u@e.com", "hash", null)));
        when(passwordEncoder.matches(anyString(), eq("hash"))).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(id, new ChangePasswordRequest("current", "current")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("отличаться");
    }

    @Test
    @DisplayName("changePassword: успех → сохраняет hash, деактивирует все сессии и refresh-токены")
    void changePassword_GivenValid_ShouldUpdateAndTerminate() {
        UUID id = UUID.randomUUID();
        UserReadModel u = user(id, "u@e.com", "old-hash", null);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("current", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("newpass", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("newpass")).thenReturn("new-hash");

        service.changePassword(id, new ChangePasswordRequest("current", "newpass"));

        assertThat(u.getPasswordHash()).isEqualTo("new-hash");
        verify(userRepository).save(u);
        verify(loginAuditRepository).deactivateAllUserSessions(id);
        verify(refreshTokenService).deleteAllUserTokens(id);
    }

    @Test
    @DisplayName("changePassword: OAuth-аккаунт (passwordHash=null) → bad request")
    void changePassword_GivenOAuthAccount_ShouldThrow() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user(id, "u@e.com", null, null)));

        assertThatThrownBy(() -> service.changePassword(id, new ChangePasswordRequest("x", "y")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("OAuth");
    }

    @Test
    @DisplayName("terminateSession: владелец сессии → deactivateById")
    void terminateSession_GivenOwner_ShouldDeactivate() {
        UUID id = UUID.randomUUID();
        LoginAudit s = new LoginAudit();
        s.setId(42);
        s.setUserId(id);
        when(loginAuditRepository.findById(42)).thenReturn(Optional.of(s));

        service.terminateSession(id, 42);

        verify(loginAuditRepository).deactivateSessionById(42);
    }

    @Test
    @DisplayName("terminateSession: чужая сессия → 'Доступ запрещен'")
    void terminateSession_GivenForeignSession_ShouldThrow() {
        UUID id = UUID.randomUUID();
        LoginAudit s = new LoginAudit();
        s.setId(42);
        s.setUserId(UUID.randomUUID());
        when(loginAuditRepository.findById(42)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.terminateSession(id, 42))
                .hasMessageContaining("Доступ запрещен");
        verify(loginAuditRepository, never()).deactivateSessionById(any());
    }

    @Test
    @DisplayName("getActiveSessions: маркирует current=true только для совпавшего refresh-hash")
    void getActiveSessions_ShouldMarkCurrent() {
        UUID id = UUID.randomUUID();
        LoginAudit s1 = new LoginAudit();
        s1.setId(1);
        s1.setUserId(id);
        s1.setRefreshTokenHash("h1");
        LoginAudit s2 = new LoginAudit();
        s2.setId(2);
        s2.setUserId(id);
        s2.setRefreshTokenHash("h2");
        when(loginAuditRepository.findByUserIdAndIsActiveTrueOrderByLoginAtDesc(id)).thenReturn(List.of(s1, s2));
        when(passwordEncoder.matches("token", "h1")).thenReturn(false);
        when(passwordEncoder.matches("token", "h2")).thenReturn(true);

        var sessions = service.getActiveSessions(id, "token");

        assertThat(sessions).hasSize(2);
        assertThat(sessions.get(0).isCurrent()).isFalse();
        assertThat(sessions.get(1).isCurrent()).isTrue();
    }

    @Test
    @DisplayName("deleteAccount DIRECTOR с организацией → публикует user.director.deleted в Rabbit")
    void deleteAccount_GivenDirectorWithOrg_ShouldPublishEvent() {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UserReadModel u = user(id, "u@e.com", "hash", null);
        u.setRole(UserRole.DIRECTOR);
        u.setOrganizationId(orgId);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        when(loginAuditRepository.findByUserIdAndIsActiveTrueOrderByLoginAtDesc(id)).thenReturn(List.of());

        service.deleteAccount(id);

        assertThat(u.getIsActive()).isFalse();
        verify(rabbitTemplate).convertAndSend(eq("sso.exchange"), eq("user.director.deleted"), any(Object.class));
    }

    @Test
    @DisplayName("deleteAccount WORKER → НЕ публикует событие")
    void deleteAccount_GivenWorker_ShouldNotPublish() {
        UUID id = UUID.randomUUID();
        UserReadModel u = user(id, "u@e.com", "hash", null);
        u.setRole(UserRole.WORKER);
        u.setOrganizationId(UUID.randomUUID());
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        when(loginAuditRepository.findByUserIdAndIsActiveTrueOrderByLoginAtDesc(id)).thenReturn(List.of());

        service.deleteAccount(id);

        assertThat(u.getIsActive()).isFalse();
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), (Object) any());
    }
}
