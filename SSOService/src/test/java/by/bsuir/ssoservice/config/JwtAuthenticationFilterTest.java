package by.bsuir.ssoservice.config;

import by.bsuir.ssoservice.service.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter — модульные тесты")
class JwtAuthenticationFilterTest {

    @Mock private JwtTokenService jwtTokenService;
    @Mock private FilterChain filterChain;

    @InjectMocks private JwtAuthenticationFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Валидный Bearer → ставит Authentication в SecurityContext с ROLE_<role>")
    void doFilter_GivenValidToken_ShouldSetAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/profile");
        request.addHeader("Authorization", "Bearer valid.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenService.validateToken("valid.jwt.token")).thenReturn(true);
        when(jwtTokenService.extractUserId("valid.jwt.token")).thenReturn("00000000-0000-0000-0000-000000000001");
        when(jwtTokenService.extractEmail("valid.jwt.token")).thenReturn("user@example.com");
        when(jwtTokenService.extractRole("valid.jwt.token")).thenReturn("DIRECTOR");

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(auth.getAuthorities()).extracting(a -> a.getAuthority())
                .containsExactly("ROLE_DIRECTOR");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Невалидный токен → SecurityContext остаётся пустым, фильтр-чейн продолжается")
    void doFilter_GivenInvalidToken_ShouldNotAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/profile");
        request.addHeader("Authorization", "Bearer bad.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenService.validateToken("bad.token")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Отсутствует Authorization → пропускает запрос без authentication")
    void doFilter_GivenNoAuthHeader_ShouldSkip() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verify(jwtTokenService, never()).validateToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("Authorization не Bearer → пропускает запрос без authentication")
    void doFilter_GivenNonBearer_ShouldSkip() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/profile");
        request.addHeader("Authorization", "Basic xxx");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Валидный токен с null userId/role → не ставит Authentication")
    void doFilter_GivenTokenWithNullClaims_ShouldNotAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/profile");
        request.addHeader("Authorization", "Bearer x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenService.validateToken("x")).thenReturn(true);
        when(jwtTokenService.extractUserId("x")).thenReturn(null);
        when(jwtTokenService.extractEmail("x")).thenReturn(null);
        when(jwtTokenService.extractRole("x")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Исключение в jwt-сервисе → фильтр не падает, цепочка продолжается")
    void doFilter_GivenJwtServiceThrows_ShouldStillContinueChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/profile");
        request.addHeader("Authorization", "Bearer broken");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenService.validateToken("broken")).thenThrow(new RuntimeException("malformed"));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("shouldNotFilter: /api/auth, /actuator, /api/internal, /error → true")
    void shouldNotFilter_GivenExcludedPaths_ShouldReturnTrue() {
        for (String p : new String[]{"/api/auth/login", "/actuator/health", "/api/test/whoami", "/api/internal/users/1", "/error"}) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRequestURI(p);
            assertThat(filter.shouldNotFilter(req)).as(p).isTrue();
        }
    }

    @Test
    @DisplayName("shouldNotFilter: обычный путь /api/profile → false")
    void shouldNotFilter_GivenRegularPath_ShouldReturnFalse() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/profile");
        assertThat(filter.shouldNotFilter(req)).isFalse();
    }

    @SuppressWarnings("unused")
    private static void ignore(HttpServletRequest a, HttpServletResponse b) {}
}
