package by.bsuir.ssoservice.utils;

import by.bsuir.ssoservice.exception.AppException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SecurityUtils Unit Tests")
class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getCurrentUserId: Given valid authentication Should return userId")
    void getCurrentUserId_GivenValidAuthentication_ShouldReturnUserId() {
        UUID userId = UUID.randomUUID();

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userId.toString(),
                null,
                Collections.emptyList()
        );

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        UUID result = SecurityUtils.getCurrentUserId();

        assertThat(result).isEqualTo(userId);
    }

    @Test
    @DisplayName("getCurrentUserId: Given no authentication Should throw unauthorized exception")
    void getCurrentUserId_GivenNoAuthentication_ShouldThrowUnauthorizedException() {
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        assertThatThrownBy(SecurityUtils::getCurrentUserId)
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не аутентифицирован");
    }

    @Test
    @DisplayName("getCurrentUserId: Given unauthenticated user Should throw unauthorized exception")
    void getCurrentUserId_GivenUnauthenticatedUser_ShouldThrowUnauthorizedException() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(false);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        assertThatThrownBy(SecurityUtils::getCurrentUserId)
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не аутентифицирован");
    }

    @Test
    @DisplayName("getCurrentUserId: Given invalid UUID format Should throw unauthorized exception")
    void getCurrentUserId_GivenInvalidUuidFormat_ShouldThrowUnauthorizedException() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "invalid-uuid",
                null,
                Collections.emptyList()
        );

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        assertThatThrownBy(SecurityUtils::getCurrentUserId)
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Неверный формат идентификатора");
    }
}

