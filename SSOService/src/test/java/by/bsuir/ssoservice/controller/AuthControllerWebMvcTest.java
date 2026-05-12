package by.bsuir.ssoservice.controller;

import by.bsuir.ssoservice.dto.request.LoginRequest;
import by.bsuir.ssoservice.dto.request.RefreshTokenRequest;
import by.bsuir.ssoservice.dto.request.RegisterDirectorRequest;
import by.bsuir.ssoservice.dto.response.AuthResponse;
import by.bsuir.ssoservice.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController — валидация DTO (MockMvc)")
class AuthControllerWebMvcTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new LocalValidatorFactoryBean() {{ afterPropertiesSet(); }})
                .build();
    }

    @Test
    @DisplayName("POST /api/auth/login без email → 400")
    void login_GivenBlankEmail_ShouldReturnBadRequest() throws Exception {
        LoginRequest req = new LoginRequest("", "secret123");
        mockMvc().perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/login с невалидным email → 400")
    void login_GivenInvalidEmail_ShouldReturnBadRequest() throws Exception {
        LoginRequest req = new LoginRequest("not-an-email", "secret123");
        mockMvc().perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/login без пароля → 400")
    void login_GivenBlankPassword_ShouldReturnBadRequest() throws Exception {
        LoginRequest req = new LoginRequest("user@example.com", "");
        mockMvc().perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/login валидное тело → 200 + токены")
    void login_GivenValidRequest_ShouldReturnTokens() throws Exception {
        AuthResponse resp = AuthResponse.of("at", "rt", 3600L);
        when(userService.login(any(), any(), any())).thenReturn(resp);

        LoginRequest req = new LoginRequest("user@example.com", "secret123");
        mockMvc().perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("at"))
                .andExpect(jsonPath("$.refreshToken").value("rt"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh c пустым токеном → 400")
    void refresh_GivenBlankToken_ShouldReturnBadRequest() throws Exception {
        RefreshTokenRequest req = new RefreshTokenRequest("");
        mockMvc().perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register/director c невалидным email → 400")
    void registerDirector_GivenInvalidEmail_ShouldReturnBadRequest() throws Exception {
        RegisterDirectorRequest req = new RegisterDirectorRequest(
                "bad-email", "secret123", "Иван", "Иванов", null
        );
        mockMvc().perform(post("/api/auth/register/director")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
