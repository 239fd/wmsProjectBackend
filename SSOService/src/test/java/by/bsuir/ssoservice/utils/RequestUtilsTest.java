package by.bsuir.ssoservice.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("RequestUtils Unit Tests")
class RequestUtilsTest {

    @Test
    @DisplayName("getClientIp: Given X-Forwarded-For header Should return first IP")
    void getClientIp_GivenXForwardedForHeader_ShouldReturnFirstIp() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.195, 70.41.3.18, 150.172.238.178");

        String ip = RequestUtils.getClientIp(request);

        assertThat(ip).isEqualTo("203.0.113.195");
    }

    @Test
    @DisplayName("getClientIp: Given single X-Forwarded-For Should return that IP")
    void getClientIp_GivenSingleXForwardedFor_ShouldReturnThatIp() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1");

        String ip = RequestUtils.getClientIp(request);

        assertThat(ip).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("getClientIp: Given X-Real-IP header Should return that IP")
    void getClientIp_GivenXRealIpHeader_ShouldReturnThatIp() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("10.0.0.1");

        String ip = RequestUtils.getClientIp(request);

        assertThat(ip).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("getClientIp: Given no headers Should return remote address")
    void getClientIp_GivenNoHeaders_ShouldReturnRemoteAddress() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        String ip = RequestUtils.getClientIp(request);

        assertThat(ip).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("getClientIp: Given empty X-Forwarded-For Should fallback to X-Real-IP")
    void getClientIp_GivenEmptyXForwardedFor_ShouldFallbackToXRealIp() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("");
        when(request.getHeader("X-Real-IP")).thenReturn("192.168.0.1");

        String ip = RequestUtils.getClientIp(request);

        assertThat(ip).isEqualTo("192.168.0.1");
    }

    @Test
    @DisplayName("getUserAgent: Given User-Agent header Should return it")
    void getUserAgent_GivenUserAgentHeader_ShouldReturnIt() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

        String userAgent = RequestUtils.getUserAgent(request);

        assertThat(userAgent).isEqualTo("Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
    }

    @Test
    @DisplayName("getUserAgent: Given no User-Agent Should return null")
    void getUserAgent_GivenNoUserAgent_ShouldReturnNull() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("User-Agent")).thenReturn(null);

        String userAgent = RequestUtils.getUserAgent(request);

        assertThat(userAgent).isNull();
    }
}

