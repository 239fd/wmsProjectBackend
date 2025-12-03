package by.bsuir.ssoservice.controller;

import by.bsuir.ssoservice.dto.request.CompleteOAuthRegistrationRequest;
import by.bsuir.ssoservice.dto.response.AuthResponse;
import by.bsuir.ssoservice.dto.response.OAuthRegistrationResponse;
import by.bsuir.ssoservice.model.enums.UserRole;
import by.bsuir.ssoservice.service.OAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthControllerTest {

    @Mock
    private OAuthService oauthService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private HttpServletResponse httpServletResponse;

    @InjectMocks
    private OAuthController oauthController;

    private AuthResponse authResponse;
    private OAuthRegistrationResponse registrationResponse;

    @BeforeEach
    void setUp() {
        authResponse = AuthResponse.of("access-token", "refresh-token", 3600L);
        registrationResponse = OAuthRegistrationResponse.builder()
                .temporaryToken("temp-token")
                .email("test@example.com")
                .fullName("Test User")
                .provider("google")
                .requiresRoleSelection(true)
                .redirectUrl("http://localhost:3000/role")
                .build();
    }

    @Test
    void initiateOAuth_ShouldRedirectToProvider() throws IOException {
        when(oauthService.getAuthorizationUrl("google", "login")).thenReturn("http://oauth-provider");

        oauthController.initiateOAuth("google", "login", httpServletResponse);

        verify(httpServletResponse).sendRedirect("http://oauth-provider");
    }

    @Test
    void handleOAuthCallback_WithAuthResponse_ShouldRedirectWithTokens() throws IOException {
        when(oauthService.handleCallback(eq("google"), eq("code"), eq("state"), anyString(), anyString()))
                .thenReturn(authResponse);
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("Mozilla");

        oauthController.handleOAuthCallback("google", "code", "state", httpServletRequest, httpServletResponse);

        verify(httpServletResponse).sendRedirect(startsWith("http://localhost:3000/auth/callback"));
    }

    @Test
    void handleOAuthCallback_WithRegistrationResponse_ShouldRedirectToRoleSelection() throws IOException {
        when(oauthService.handleCallback(eq("google"), eq("code"), eq("state"), anyString(), anyString()))
                .thenReturn(registrationResponse);
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("Mozilla");

        oauthController.handleOAuthCallback("google", "code", "state", httpServletRequest, httpServletResponse);

        verify(httpServletResponse).sendRedirect(startsWith("http://localhost:3000/role"));
    }

    @Test
    void completeRegistration_ShouldReturnAuthResponse() {
        CompleteOAuthRegistrationRequest request = new CompleteOAuthRegistrationRequest("temp-token", UserRole.DIRECTOR, null, null);
        when(oauthService.completeRegistration(eq(request), anyString(), anyString())).thenReturn(authResponse);
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("Mozilla");

        ResponseEntity<AuthResponse> response = oauthController.completeRegistration(request, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(authResponse);
    }
}
