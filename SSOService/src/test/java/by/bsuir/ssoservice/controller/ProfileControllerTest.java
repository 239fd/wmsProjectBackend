package by.bsuir.ssoservice.controller;

import by.bsuir.ssoservice.dto.response.UserResponse;
import by.bsuir.ssoservice.model.enums.UserRole;
import by.bsuir.ssoservice.service.ProfileService;
import by.bsuir.ssoservice.utils.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private ProfileService profileService;

    @InjectMocks
    private ProfileController profileController;

    private MockedStatic<SecurityUtils> securityUtilsMockedStatic;

    @BeforeEach
    void setUp() {
        securityUtilsMockedStatic = mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMockedStatic.close();
    }

    @Test
    void givenAuthenticatedUser_whenGetCurrentUser_thenReturnsUserProfile() {
        UUID userId = UUID.randomUUID();
        UserResponse expectedResponse = new UserResponse(
                userId,
                "test@example.com",
                "testuser",
                UserRole.WORKER,
                null,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
        when(profileService.getUserProfile(userId)).thenReturn(expectedResponse);

        ResponseEntity<UserResponse> responseEntity = profileController.getCurrentUser();

        assertThat(responseEntity.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().userId()).isEqualTo(userId);
        assertThat(responseEntity.getBody().fullName()).isEqualTo("testuser");
    }
}
