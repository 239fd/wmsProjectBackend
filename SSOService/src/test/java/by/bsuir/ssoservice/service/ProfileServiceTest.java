package by.bsuir.ssoservice.service;

import by.bsuir.ssoservice.dto.request.UpdateProfileRequest;
import by.bsuir.ssoservice.dto.response.UserResponse;
import by.bsuir.ssoservice.model.entity.LoginAudit;
import by.bsuir.ssoservice.model.entity.UserReadModel;
import by.bsuir.ssoservice.model.enums.AuthProvider;
import by.bsuir.ssoservice.model.enums.UserRole;
import by.bsuir.ssoservice.repository.UserReadModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileService Unit Tests")
class ProfileServiceTest {

    @Mock
    private UserReadModelRepository readModelRepository;

    @Mock
    private by.bsuir.ssoservice.repository.LoginAuditRepository loginAuditRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private ProfileService profileService;

    @Test
    @DisplayName("getUserProfile: Given existing user ID When getting profile Then should return user response")
    void getUserProfile_GivenExistingUserId_WhenGettingProfile_ThenShouldReturnUserResponse() {
        UUID userId = UUID.randomUUID();
        UserReadModel user = UserReadModel.builder()
                .userId(userId)
                .email("john.doe@example.com")
                .fullName("John Doe")
                .role(UserRole.DIRECTOR)
                .provider(AuthProvider.LOCAL)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(readModelRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse response = profileService.getUserProfile(userId);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("john.doe@example.com");
        assertThat(response.fullName()).isEqualTo("John Doe");
        assertThat(response.role()).isEqualTo(UserRole.DIRECTOR);

        verify(readModelRepository).findById(userId);
    }

    @Test
    @DisplayName("getUserProfile: Given non-existing user ID When getting profile Then should throw not found exception")
    void getUserProfile_GivenNonExistingUserId_WhenGettingProfile_ThenShouldThrowNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(readModelRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getUserProfile(userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Пользователь не найден");

        verify(readModelRepository).findById(userId);
    }

    @Test
    @DisplayName("updateProfile: Given valid request When updating Then should update and return response")
    void updateProfile_GivenValidRequest_WhenUpdating_ThenShouldUpdateAndReturnResponse() {
        UUID userId = UUID.randomUUID();
        UpdateProfileRequest request = new UpdateProfileRequest(
                "Jane",
                "Smith",
                null,
                "jane.smith@example.com"
        );

        UserReadModel existingUser = UserReadModel.builder()
                .userId(userId)
                .email("old@example.com")
                .fullName("Old Name")
                .role(UserRole.DIRECTOR)
                .provider(AuthProvider.LOCAL)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(readModelRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(readModelRepository.findByEmail("jane.smith@example.com")).thenReturn(Optional.empty());
        when(readModelRepository.save(any(UserReadModel.class))).thenAnswer(invocation -> {
            UserReadModel model = invocation.getArgument(0);
            return model;
        });

        UserResponse response = profileService.updateProfile(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.fullName()).isEqualTo("Smith Jane");
        assertThat(response.email()).isEqualTo("jane.smith@example.com");

        verify(readModelRepository).findById(userId);
        verify(readModelRepository).findByEmail("jane.smith@example.com");
        verify(readModelRepository).save(any(UserReadModel.class));
    }

    @Test
    @DisplayName("updateProfile: Given duplicate email When updating Then should throw conflict exception")
    void updateProfile_GivenDuplicateEmail_WhenUpdating_ThenShouldThrowConflictException() {
        UUID userId = UUID.randomUUID();
        UpdateProfileRequest request = new UpdateProfileRequest(
                "Jane",
                "Smith",
                null,
                "existing@example.com"
        );

        UserReadModel existingUser = UserReadModel.builder()
                .userId(userId)
                .email("current@example.com")
                .fullName("Current Name")
                .role(UserRole.DIRECTOR)
                .provider(AuthProvider.LOCAL)
                .isActive(true)
                .build();

        when(readModelRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(readModelRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(new UserReadModel()));

        assertThatThrownBy(() -> profileService.updateProfile(userId, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email уже используется");

        verify(readModelRepository).findById(userId);
        verify(readModelRepository).findByEmail("existing@example.com");
        verify(readModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProfile: Given same email When updating Then should not check duplicate")
    void updateProfile_GivenSameEmail_WhenUpdating_ThenShouldNotCheckDuplicate() {
        UUID userId = UUID.randomUUID();
        String currentEmail = "same@example.com";

        UpdateProfileRequest request = new UpdateProfileRequest(
                "Updated",
                "Name",
                null,
                currentEmail
        );

        UserReadModel existingUser = UserReadModel.builder()
                .userId(userId)
                .email(currentEmail)
                .fullName("Old Name")
                .role(UserRole.DIRECTOR)
                .provider(AuthProvider.LOCAL)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(readModelRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(readModelRepository.save(any(UserReadModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = profileService.updateProfile(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.fullName()).isEqualTo("Name Updated");

        verify(readModelRepository).findById(userId);
        verify(readModelRepository, never()).findByEmail(anyString());
        verify(readModelRepository).save(any(UserReadModel.class));
    }

    @Test
    @DisplayName("uploadPhoto: Given valid photo Should upload and return base64")
    void uploadPhoto_GivenValidPhoto_ShouldUploadAndReturnBase64() throws Exception {
        UUID userId = UUID.randomUUID();
        byte[] photoBytes = "test-image".getBytes();

        UserReadModel user = UserReadModel.builder()
                .userId(userId)
                .email("test@example.com")
                .fullName("Test User")
                .role(UserRole.DIRECTOR)
                .provider(AuthProvider.LOCAL)
                .isActive(true)
                .build();

        org.springframework.web.multipart.MultipartFile photo = mock(org.springframework.web.multipart.MultipartFile.class);
        when(photo.getSize()).thenReturn(1024L);
        when(photo.getContentType()).thenReturn("image/jpeg");
        when(photo.getBytes()).thenReturn(photoBytes);

        when(readModelRepository.findById(userId)).thenReturn(Optional.of(user));
        when(readModelRepository.save(any(UserReadModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String result = profileService.uploadPhoto(userId, photo);

        assertThat(result).isNotNull();
        verify(readModelRepository).findById(userId);
        verify(readModelRepository).save(any(UserReadModel.class));
    }

    @Test
    @DisplayName("uploadPhoto: Given photo too large Should throw exception")
    void uploadPhoto_GivenPhotoTooLarge_ShouldThrowException() {
        UUID userId = UUID.randomUUID();
        UserReadModel user = UserReadModel.builder()
                .userId(userId)
                .email("test@example.com")
                .build();

        org.springframework.web.multipart.MultipartFile photo = mock(org.springframework.web.multipart.MultipartFile.class);
        when(photo.getSize()).thenReturn(6L * 1024 * 1024);

        when(readModelRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> profileService.uploadPhoto(userId, photo))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("не должен превышать 5 МБ");

        verify(readModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("uploadPhoto: Given invalid content type Should throw exception")
    void uploadPhoto_GivenInvalidContentType_ShouldThrowException() {
        UUID userId = UUID.randomUUID();
        UserReadModel user = UserReadModel.builder()
                .userId(userId)
                .email("test@example.com")
                .build();

        org.springframework.web.multipart.MultipartFile photo = mock(org.springframework.web.multipart.MultipartFile.class);
        when(photo.getSize()).thenReturn(1024L);
        when(photo.getContentType()).thenReturn("application/pdf");

        when(readModelRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> profileService.uploadPhoto(userId, photo))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("должен быть изображением");

        verify(readModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("deletePhoto: Given user with photo Should delete photo")
    void deletePhoto_GivenUserWithPhoto_ShouldDeletePhoto() {
        UUID userId = UUID.randomUUID();
        UserReadModel user = UserReadModel.builder()
                .userId(userId)
                .email("test@example.com")
                .photo("photo-bytes".getBytes())
                .build();

        when(readModelRepository.findById(userId)).thenReturn(Optional.of(user));
        when(readModelRepository.save(any(UserReadModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        profileService.deletePhoto(userId);

        verify(readModelRepository).findById(userId);
        verify(readModelRepository).save(any(UserReadModel.class));
    }

    @Test
    @DisplayName("deletePhoto: Given user without photo Should not save")
    void deletePhoto_GivenUserWithoutPhoto_ShouldNotSave() {
        UUID userId = UUID.randomUUID();
        UserReadModel user = UserReadModel.builder()
                .userId(userId)
                .email("test@example.com")
                .photo(null)
                .build();

        when(readModelRepository.findById(userId)).thenReturn(Optional.of(user));

        profileService.deletePhoto(userId);

        verify(readModelRepository).findById(userId);
        verify(readModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("getActiveSessions: Should return list of active sessions")
    void getActiveSessions_ShouldReturnListOfActiveSessions() {
        UUID userId = UUID.randomUUID();
        LoginAudit session1 = LoginAudit.builder()
                .id(1)
                .userId(userId)
                .ipAddress("127.0.0.1")
                .userAgent("Mozilla")
                .loginAt(LocalDateTime.now())
                .isActive(true)
                .build();

        LoginAudit session2 = LoginAudit.builder()
                .id(2)
                .userId(userId)
                .ipAddress("192.168.1.1")
                .userAgent("Chrome")
                .loginAt(LocalDateTime.now().minusHours(1))
                .isActive(true)
                .build();

        when(loginAuditRepository.findByUserIdAndIsActiveTrueOrderByLoginAtDesc(userId))
                .thenReturn(List.of(session1, session2));

        var sessions = profileService.getActiveSessions(userId, "current-token");

        assertThat(sessions).hasSize(2);
        assertThat(sessions.get(0).ipAddress()).isEqualTo("127.0.0.1");
        verify(loginAuditRepository).findByUserIdAndIsActiveTrueOrderByLoginAtDesc(userId);
    }

    @Test
    @DisplayName("terminateSession: Given valid session Should terminate it")
    void terminateSession_GivenValidSession_ShouldTerminateIt() {
        UUID userId = UUID.randomUUID();
        Integer sessionId = 1;

        LoginAudit session = LoginAudit.builder()
                .id(sessionId)
                .userId(userId)
                .isActive(true)
                .build();

        when(loginAuditRepository.findById(sessionId)).thenReturn(Optional.of(session));

        profileService.terminateSession(userId, sessionId);

        verify(loginAuditRepository).findById(sessionId);
        verify(loginAuditRepository).deactivateSessionById(sessionId);
    }

    @Test
    @DisplayName("terminateSession: Given invalid session ID Should throw exception")
    void terminateSession_GivenInvalidSessionId_ShouldThrowException() {
        UUID userId = UUID.randomUUID();
        Integer sessionId = 999;

        when(loginAuditRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.terminateSession(userId, sessionId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Сессия не найдена");

        verify(loginAuditRepository).findById(sessionId);
        verify(loginAuditRepository, never()).deactivateSessionById(any());
    }

    @Test
    @DisplayName("terminateSession: Given wrong user Should throw exception")
    void terminateSession_GivenWrongUser_ShouldThrowException() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Integer sessionId = 1;

        LoginAudit session = LoginAudit.builder()
                .id(sessionId)
                .userId(otherUserId)
                .isActive(true)
                .build();

        when(loginAuditRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> profileService.terminateSession(userId, sessionId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Доступ запрещен");

        verify(loginAuditRepository).findById(sessionId);
        verify(loginAuditRepository, never()).deactivateSessionById(any());
    }

    @Test
    @DisplayName("terminateAllSessions: Should terminate all user sessions")
    void terminateAllSessions_ShouldTerminateAllUserSessions() {
        UUID userId = UUID.randomUUID();
        String currentToken = "current-token";

        profileService.terminateAllSessions(userId, currentToken);

        verify(loginAuditRepository).deactivateAllUserSessions(userId);
        verify(refreshTokenService).deleteAllUserTokens(userId);
    }

    @Test
    @DisplayName("deleteAccount: Should deactivate user account")
    void deleteAccount_ShouldDeactivateUserAccount() {
        UUID userId = UUID.randomUUID();
        UserReadModel user = UserReadModel.builder()
                .userId(userId)
                .email("test@example.com")
                .isActive(true)
                .build();

        LoginAudit session = LoginAudit.builder()
                .id(1)
                .userId(userId)
                .refreshTokenHash("token-hash")
                .isActive(true)
                .build();

        when(readModelRepository.findById(userId)).thenReturn(Optional.of(user));
        when(loginAuditRepository.findByUserIdAndIsActiveTrueOrderByLoginAtDesc(userId))
                .thenReturn(List.of(session));
        when(readModelRepository.save(any(UserReadModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        profileService.deleteAccount(userId);

        verify(readModelRepository).findById(userId);
        verify(loginAuditRepository).deactivateAllUserSessions(userId);
        verify(refreshTokenService).deleteRefreshToken("token-hash");
        verify(readModelRepository).save(any(UserReadModel.class));
    }

    @Test
    @DisplayName("updateProfile: Given profile with middle name Should include it in full name")
    void updateProfile_GivenProfileWithMiddleName_ShouldIncludeItInFullName() {
        UUID userId = UUID.randomUUID();
        UpdateProfileRequest request = new UpdateProfileRequest(
                "John",
                "Doe",
                "Michael",
                "john.doe@example.com"
        );

        UserReadModel existingUser = UserReadModel.builder()
                .userId(userId)
                .email("john.doe@example.com")
                .fullName("Old Name")
                .role(UserRole.DIRECTOR)
                .provider(AuthProvider.LOCAL)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(readModelRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(readModelRepository.save(any(UserReadModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = profileService.updateProfile(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.fullName()).isEqualTo("Doe John Michael");

        verify(readModelRepository).findById(userId);
        verify(readModelRepository).save(any(UserReadModel.class));
    }

    @Test
    @DisplayName("getUserProfile: Given user with photo Should return base64 encoded photo")
    void getUserProfile_GivenUserWithPhoto_ShouldReturnBase64EncodedPhoto() {
        UUID userId = UUID.randomUUID();
        byte[] photoBytes = "test-photo".getBytes();

        UserReadModel user = UserReadModel.builder()
                .userId(userId)
                .email("john.doe@example.com")
                .fullName("John Doe")
                .role(UserRole.DIRECTOR)
                .provider(AuthProvider.LOCAL)
                .photo(photoBytes)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(readModelRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse response = profileService.getUserProfile(userId);

        assertThat(response).isNotNull();
        assertThat(response.photoBase64()).isNotNull();
        assertThat(response.photoBase64()).isNotEmpty();

        verify(readModelRepository).findById(userId);
    }

    @Test
    @DisplayName("uploadPhoto: Given null content type Should throw exception")
    void uploadPhoto_GivenNullContentType_ShouldThrowException() {
        UUID userId = UUID.randomUUID();
        UserReadModel user = UserReadModel.builder()
                .userId(userId)
                .email("test@example.com")
                .build();

        org.springframework.web.multipart.MultipartFile photo = mock(org.springframework.web.multipart.MultipartFile.class);
        when(photo.getSize()).thenReturn(1024L);
        when(photo.getContentType()).thenReturn(null);

        when(readModelRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> profileService.uploadPhoto(userId, photo))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("должен быть изображением");

        verify(readModelRepository, never()).save(any());
    }
}
