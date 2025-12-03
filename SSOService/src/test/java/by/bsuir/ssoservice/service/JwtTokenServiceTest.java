package by.bsuir.ssoservice.service;

import by.bsuir.ssoservice.model.enums.UserRole;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtTokenService Unit Tests")
class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        keyPair = keyGen.generateKeyPair();
        jwtTokenService = new JwtTokenService(keyPair);
    }

    @Test
    @DisplayName("generateAccessToken: Should generate valid JWT token with correct claims")
    void generateAccessToken_ShouldGenerateValidJwtTokenWithCorrectClaims() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        UserRole role = UserRole.DIRECTOR;

        String token = jwtTokenService.generateAccessToken(userId, email, role);

        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();

        SignedJWT signedJWT = SignedJWT.parse(token);
        assertThat(signedJWT.getJWTClaimsSet().getSubject()).isEqualTo(userId.toString());
        assertThat(signedJWT.getJWTClaimsSet().getStringClaim("email")).isEqualTo(email);
        assertThat(signedJWT.getJWTClaimsSet().getStringClaim("role")).isEqualTo(role.name());
        assertThat(signedJWT.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(signedJWT.getHeader().getKeyID()).isEqualTo("sso-key-id");
        assertThat(signedJWT.getJWTClaimsSet().getIssuer()).isEqualTo("http://localhost:7777");
    }

    @Test
    @DisplayName("generateRefreshToken: Should generate unique UUID strings")
    void generateRefreshToken_ShouldGenerateUniqueUuidStrings() {
        String token1 = jwtTokenService.generateRefreshToken();
        String token2 = jwtTokenService.generateRefreshToken();

        assertThat(token1).isNotNull();
        assertThat(token2).isNotNull();
        assertThat(token1).isNotEqualTo(token2);

        UUID.fromString(token1);
        UUID.fromString(token2);
    }

    @Test
    @DisplayName("validateAccessToken: Given valid token Should return true")
    void validateAccessToken_GivenValidToken_ShouldReturnTrue() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenService.generateAccessToken(userId, "test@example.com", UserRole.DIRECTOR);

        boolean isValid = jwtTokenService.validateAccessToken(token);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("validateAccessToken: Given invalid token Should return false")
    void validateAccessToken_GivenInvalidToken_ShouldReturnFalse() {
        boolean isValid = jwtTokenService.validateAccessToken("invalid.token.here");

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("getUserIdFromToken: Given valid token Should extract userId")
    void getUserIdFromToken_GivenValidToken_ShouldExtractUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenService.generateAccessToken(userId, "test@example.com", UserRole.DIRECTOR);

        UUID extractedUserId = jwtTokenService.getUserIdFromToken(token);

        assertThat(extractedUserId).isEqualTo(userId);
    }

    @Test
    @DisplayName("getUserIdFromToken: Given invalid token Should return null")
    void getUserIdFromToken_GivenInvalidToken_ShouldReturnNull() {
        UUID extractedUserId = jwtTokenService.getUserIdFromToken("invalid.token");

        assertThat(extractedUserId).isNull();
    }

    @Test
    @DisplayName("extractEmail: Given valid token Should extract email")
    void extractEmail_GivenValidToken_ShouldExtractEmail() {
        String email = "test@example.com";
        String token = jwtTokenService.generateAccessToken(UUID.randomUUID(), email, UserRole.DIRECTOR);

        String extractedEmail = jwtTokenService.extractEmail(token);

        assertThat(extractedEmail).isEqualTo(email);
    }

    @Test
    @DisplayName("extractEmail: Given invalid token Should return null")
    void extractEmail_GivenInvalidToken_ShouldReturnNull() {
        String extractedEmail = jwtTokenService.extractEmail("invalid.token");

        assertThat(extractedEmail).isNull();
    }

    @Test
    @DisplayName("extractRole: Given valid token Should extract role")
    void extractRole_GivenValidToken_ShouldExtractRole() {
        UserRole role = UserRole.WORKER;
        String token = jwtTokenService.generateAccessToken(UUID.randomUUID(), "test@example.com", role);

        String extractedRole = jwtTokenService.extractRole(token);

        assertThat(extractedRole).isEqualTo(role.name());
    }

    @Test
    @DisplayName("extractRole: Given invalid token Should return null")
    void extractRole_GivenInvalidToken_ShouldReturnNull() {
        String extractedRole = jwtTokenService.extractRole("invalid.token");

        assertThat(extractedRole).isNull();
    }

    @Test
    @DisplayName("extractUserId: Given valid token Should extract userId as string")
    void extractUserId_GivenValidToken_ShouldExtractUserIdAsString() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenService.generateAccessToken(userId, "test@example.com", UserRole.DIRECTOR);

        String extractedUserId = jwtTokenService.extractUserId(token);

        assertThat(extractedUserId).isEqualTo(userId.toString());
    }

    @Test
    @DisplayName("extractUserId: Given invalid token Should return null")
    void extractUserId_GivenInvalidToken_ShouldReturnNull() {
        String extractedUserId = jwtTokenService.extractUserId("invalid.token");

        assertThat(extractedUserId).isNull();
    }

    @Test
    @DisplayName("validateToken: Should delegate to validateAccessToken")
    void validateToken_ShouldDelegateToValidateAccessToken() {
        UUID userId = UUID.randomUUID();
        String validToken = jwtTokenService.generateAccessToken(userId, "test@example.com", UserRole.DIRECTOR);
        String invalidToken = "invalid.token";

        assertThat(jwtTokenService.validateToken(validToken)).isTrue();
        assertThat(jwtTokenService.validateToken(invalidToken)).isFalse();
    }

    @Test
    @DisplayName("getAccessTokenValidity: Should return 15 minutes in seconds")
    void getAccessTokenValidity_ShouldReturn15MinutesInSeconds() {
        long validity = jwtTokenService.getAccessTokenValidity();

        assertThat(validity).isEqualTo(15 * 60);
    }

    @Test
    @DisplayName("getRefreshTokenValidity: Should return 7 days in seconds")
    void getRefreshTokenValidity_ShouldReturn7DaysInSeconds() {
        long validity = jwtTokenService.getRefreshTokenValidity();

        assertThat(validity).isEqualTo(7 * 24 * 60 * 60);
    }
}

