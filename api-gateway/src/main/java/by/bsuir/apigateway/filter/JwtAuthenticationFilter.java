package by.bsuir.apigateway.filter;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;





@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.public-key:}")
    private String publicKeyString;

    @Value("${sso.service.url:http://localhost:8000}")
    private String ssoServiceUrl;

    private RSAPublicKey cachedPublicKey;
    private long lastKeyFetchTime = 0;
    private static final long KEY_CACHE_DURATION = 3600000;

    private static final List<String> EXCLUDED_PATHS = List.of(
            "/sso-service/api/auth/login",
            "/sso-service/api/auth/register",
            "/sso-service/api/auth/refresh",
            "/sso-service/api/auth/logout",
            "/sso-service/api/auth/public-key",
            "/sso-service/api/oauth",
            "/actuator",
            "/prometheus",
            "/eureka"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();


        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {

            SignedJWT signedJWT = SignedJWT.parse(token);


            RSAPublicKey publicKey = getPublicKey();


            JWSVerifier verifier = new RSASSAVerifier(publicKey);

            if (!signedJWT.verify(verifier)) {
                log.warn("Invalid JWT signature for path: {}", path);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }


            Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (expirationTime.before(new Date())) {
                log.warn("Expired JWT token for path: {}", path);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }


            String userId = signedJWT.getJWTClaimsSet().getSubject();
            String email = signedJWT.getJWTClaimsSet().getStringClaim("email");
            String role = signedJWT.getJWTClaimsSet().getStringClaim("role");


            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Email", email)
                    .header("X-User-Role", role)
                    .build();

            log.debug("Authenticated user: {} (role: {}) for path: {}", email, role, path);

            return chain.filter(exchange.mutate().request(modifiedRequest).build());

        } catch (Exception e) {
            log.error("JWT validation error: {}", e.getMessage(), e);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private boolean isExcludedPath(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }




    private RSAPublicKey getPublicKey() throws Exception {
        long currentTime = System.currentTimeMillis();


        if (cachedPublicKey != null && (currentTime - lastKeyFetchTime) < KEY_CACHE_DURATION) {
            return cachedPublicKey;
        }


        if (publicKeyString != null && !publicKeyString.isEmpty() && !publicKeyString.contains("yourpublickey")) {
            try {
                cachedPublicKey = loadPublicKeyFromPEM(publicKeyString);
                lastKeyFetchTime = currentTime;
                log.info("Loaded public key from configuration");
                return cachedPublicKey;
            } catch (Exception e) {
                log.warn("Failed to load public key from configuration: {}", e.getMessage());
            }
        }


        try {
            String publicKeyPEM = fetchPublicKeyFromSSOService();
            cachedPublicKey = loadPublicKeyFromPEM(publicKeyPEM);
            lastKeyFetchTime = currentTime;
            log.info("Fetched and cached public key from SSO Service");
            return cachedPublicKey;
        } catch (Exception e) {
            log.error("Failed to fetch public key from SSO Service: {}", e.getMessage());
            throw new RuntimeException("Cannot validate JWT: public key not available", e);
        }
    }




    private String fetchPublicKeyFromSSOService() throws Exception {
        try {

            java.net.URL url = new java.net.URL(ssoServiceUrl + "/api/auth/public-key");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new RuntimeException("SSO Service returned status: " + responseCode);
            }

            java.io.BufferedReader in = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();


            String jsonResponse = response.toString();

            int startIndex = jsonResponse.indexOf("\"publicKey\":\"") + 13;
            int endIndex = jsonResponse.indexOf("\"", startIndex);
            String publicKeyPEM = jsonResponse.substring(startIndex, endIndex);


            publicKeyPEM = publicKeyPEM.replace("\\n", "\n");

            return publicKeyPEM;
        } catch (Exception e) {
            log.error("Error fetching public key from SSO Service", e);
            throw e;
        }
    }

    private RSAPublicKey loadPublicKeyFromPEM(String publicKeyPEM) throws Exception {
        String publicKeyContent = publicKeyPEM
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(publicKeyContent);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(spec);
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
