package by.bsuir.ssoservice.service;

import by.bsuir.ssoservice.model.enums.UserRole;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtTokenService — модульные тесты")
class JwtTokenServiceTest {

    private JwtTokenService service;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
        service = new JwtTokenService(keyPair, 14400L, 2592000L, "wms-sso");
    }

    @Test
    @DisplayName("generateAccessToken: содержит sub/email/role/orgId/warehouseId и подписан RS256")
    void generateAccessToken_ShouldContainClaimsAndBeRs256() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        String token = service.generateAccessToken(userId, "ivan@example.com", UserRole.WORKER, orgId, warehouseId);

        assertThat(token).isNotBlank();
        SignedJWT jwt = SignedJWT.parse(token);
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.getStringClaim("email")).isEqualTo("ivan@example.com");
        assertThat(claims.getStringClaim("role")).isEqualTo("WORKER");
        assertThat(claims.getStringClaim("organizationId")).isEqualTo(orgId.toString());
        assertThat(claims.getStringClaim("warehouseId")).isEqualTo(warehouseId.toString());
        assertThat(claims.getJWTID()).isNotBlank();
        assertThat(claims.getExpirationTime()).isAfter(new Date());

        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) keyPair.getPublic());
        assertThat(jwt.verify(verifier)).isTrue();
    }

    @Test
    @DisplayName("generateAccessToken: orgId/warehouseId == null → claims отсутствуют")
    void generateAccessToken_GivenNullOrgAndWarehouse_ShouldOmitClaims() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = service.generateAccessToken(userId, "x@y.z", UserRole.DIRECTOR, null, null);

        JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
        assertThat(claims.getStringClaim("organizationId")).isNull();
        assertThat(claims.getStringClaim("warehouseId")).isNull();
    }

    @Test
    @DisplayName("generateAccessToken: TTL равен accessTokenValidity")
    void generateAccessToken_TtlMatchesAccessTokenValidity() throws Exception {
        UUID userId = UUID.randomUUID();
        long start = System.currentTimeMillis();
        String token = service.generateAccessToken(userId, "x@y.z", UserRole.WORKER, null, null);
        long end = System.currentTimeMillis();

        Date exp = SignedJWT.parse(token).getJWTClaimsSet().getExpirationTime();
        long ttlMs = exp.getTime() - start;

        assertThat(ttlMs).isBetween(14_400_000L - 1000, (end - start) + 14_400_000L + 1000);
    }

    @Test
    @DisplayName("generateRefreshToken: возвращает валидный UUID-string")
    void generateRefreshToken_ShouldReturnUuidString() {
        String refresh = service.generateRefreshToken();
        assertThat(UUID.fromString(refresh)).isNotNull();
    }

    @Test
    @DisplayName("validateAccessToken: только что созданный токен → true")
    void validateAccessToken_GivenFreshToken_ShouldReturnTrue() {
        String token = service.generateAccessToken(UUID.randomUUID(), "x@y.z", UserRole.WORKER, null, null);
        assertThat(service.validateAccessToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateAccessToken: токен подписан другим ключом → false")
    void validateAccessToken_GivenForeignSignature_ShouldReturnFalse() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        JwtTokenService foreign = new JwtTokenService(gen.generateKeyPair(), 14400L, 2592000L, "wms-sso");
        String foreignToken = foreign.generateAccessToken(UUID.randomUUID(), "x@y.z", UserRole.WORKER, null, null);

        assertThat(service.validateAccessToken(foreignToken)).isFalse();
    }

    @Test
    @DisplayName("validateAccessToken: токен истёк → false")
    void validateAccessToken_GivenExpired_ShouldReturnFalse() {
        JwtTokenService shortLived = new JwtTokenService(keyPair, -1L, 2592000L, "wms-sso");
        String expired = shortLived.generateAccessToken(UUID.randomUUID(), "x@y.z", UserRole.WORKER, null, null);

        assertThat(service.validateAccessToken(expired)).isFalse();
    }

    @Test
    @DisplayName("validateAccessToken: мусор → false")
    void validateAccessToken_GivenGarbage_ShouldReturnFalse() {
        assertThat(service.validateAccessToken("not.a.jwt")).isFalse();
        assertThat(service.validateAccessToken("")).isFalse();
    }

    @Test
    @DisplayName("getUserIdFromToken: возвращает UUID из sub")
    void getUserIdFromToken_ShouldReturnSubject() {
        UUID userId = UUID.randomUUID();
        String token = service.generateAccessToken(userId, "x@y.z", UserRole.WORKER, null, null);

        assertThat(service.getUserIdFromToken(token)).isEqualTo(userId);
    }

    @Test
    @DisplayName("getUserIdFromToken: мусор → null без выброса исключения")
    void getUserIdFromToken_GivenGarbage_ShouldReturnNull() {
        assertThat(service.getUserIdFromToken("not.a.jwt")).isNull();
    }

    @Test
    @DisplayName("extractEmail/extractRole: достают claims")
    void extractEmailAndRole_ShouldReturnClaims() {
        String token = service.generateAccessToken(
                UUID.randomUUID(), "ivan@example.com", UserRole.ACCOUNTANT, null, null);

        assertThat(service.extractEmail(token)).isEqualTo("ivan@example.com");
        assertThat(service.extractRole(token)).isEqualTo("ACCOUNTANT");
    }

    @Test
    @DisplayName("getAccessTokenValidity / getRefreshTokenValidity: отдают сконфигурированные значения")
    void getValidityGetters_ShouldReturnConfiguredValues() {
        assertThat(service.getAccessTokenValidity()).isEqualTo(14400L);
        assertThat(service.getRefreshTokenValidity()).isEqualTo(2592000L);
    }
}
