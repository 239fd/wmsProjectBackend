package by.bsuir.ssoservice.service;

import by.bsuir.ssoservice.model.enums.UserRole;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
public class JwtTokenService {

    private final KeyPair keyPair;
    private final long accessTokenValidity;
    private final long refreshTokenValidity;

    public JwtTokenService(
            KeyPair keyPair,
            @Value("${app.security.jwt.access-ttl-seconds:14400}") long accessTokenValidity,
            @Value("${app.security.jwt.refresh-ttl-seconds:2592000}") long refreshTokenValidity) {
        this.keyPair = keyPair;
        this.accessTokenValidity = accessTokenValidity;
        this.refreshTokenValidity = refreshTokenValidity;
    }

    public String generateAccessToken(UUID userId, String email, UserRole role, UUID organizationId, UUID warehouseId) {
        try {
            Instant now = Instant.now();

            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .claim("email", email)
                    .claim("role", role.name())
                    .issuer("http://localhost:7777")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(accessTokenValidity)))
                    .jwtID(UUID.randomUUID().toString());

            if (organizationId != null) {
                builder.claim("organizationId", organizationId.toString());
            }
            if (warehouseId != null) {
                builder.claim("warehouseId", warehouseId.toString());
            }
            JWTClaimsSet claimsSet = builder.build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID("sso-key-id")
                            .build(),
                    claimsSet
            );

            JWSSigner signer = new RSASSASigner((RSAPrivateKey) keyPair.getPrivate());
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (JOSEException e) {
            log.error("Error generating access token", e);
            throw new RuntimeException("Failed to generate access token", e);
        }
    }

    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    public boolean validateAccessToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) keyPair.getPublic());

            if (!signedJWT.verify(verifier)) {
                return false;
            }

            Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (expirationTime == null) {
                return false;
            }

            if (!expirationTime.after(new Date())) {
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("Error validating token: {}", e.getMessage());
            return false;
        }
    }

    public UUID getUserIdFromToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            return UUID.fromString(signedJWT.getJWTClaimsSet().getSubject());
        } catch (Exception e) {
            log.error("Error extracting userId from token", e);
            return null;
        }
    }

    public boolean validateToken(String token) {
        return validateAccessToken(token);
    }

    public String extractUserId(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            return signedJWT.getJWTClaimsSet().getSubject();
        } catch (Exception e) {
            log.error("Error extracting userId from token", e);
            return null;
        }
    }

    public String extractEmail(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            return signedJWT.getJWTClaimsSet().getStringClaim("email");
        } catch (Exception e) {
            log.error("Error extracting email from token", e);
            return null;
        }
    }

    public String extractRole(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            return signedJWT.getJWTClaimsSet().getStringClaim("role");
        } catch (Exception e) {
            log.error("Error extracting role from token", e);
            return null;
        }
    }

    public long getAccessTokenValidity() {
        return accessTokenValidity;
    }

    public long getRefreshTokenValidity() {
        return refreshTokenValidity;
    }
}
