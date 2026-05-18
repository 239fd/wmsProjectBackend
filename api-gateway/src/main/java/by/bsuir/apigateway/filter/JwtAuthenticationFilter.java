package by.bsuir.apigateway.filter;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String REDIS_KEY = "gw:jwt-public-key";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${jwt.public-key:}")
    private String publicKeyString;

    @Value("${sso.service.url:http://localhost:8000}")
    private String ssoServiceUrl;

    private static final List<String> EXCLUDED_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register/director",
            "/api/auth/register/invitation",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/public-key",
            "/api/oauth",
            "/api/invitations/validate",

            "/sso-service/api/auth/login",
            "/sso-service/api/auth/register/director",
            "/sso-service/api/auth/register/invitation",
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
            return unauthorized(exchange);
        }

        String token = authHeader.substring(7);

        return Mono.fromCallable(() -> validateAndBuildRequest(token, path, exchange))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(modifiedRequest -> chain.filter(exchange.mutate().request(modifiedRequest).build()))
                .onErrorResume(e -> {
                    log.error("JWT validation error for path {}: {}", path, e.getMessage(), e);
                    return unauthorized(exchange);
                });
    }

    private ServerHttpRequest validateAndBuildRequest(String token, String path, ServerWebExchange exchange) throws Exception {
        SignedJWT signedJWT = SignedJWT.parse(token);

        RSAPublicKey publicKey = getPublicKey();
        if (!signedJWT.verify(new RSASSAVerifier(publicKey))) {
            log.warn("Invalid JWT signature with cached key, refreshing public key for path: {}", path);
            redisTemplate.delete(REDIS_KEY).block();
            RSAPublicKey freshKey = getPublicKey();
            if (!signedJWT.verify(new RSASSAVerifier(freshKey))) {
                throw new IllegalStateException("Invalid JWT signature (also failed with fresh public key)");
            }
        }

        Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();
        if (expirationTime == null || expirationTime.before(new Date())) {
            throw new IllegalStateException("Expired or invalid JWT exp claim");
        }

        String userId = signedJWT.getJWTClaimsSet().getSubject();
        String email = signedJWT.getJWTClaimsSet().getStringClaim("email");
        String role = signedJWT.getJWTClaimsSet().getStringClaim("role");
        String organizationId = signedJWT.getJWTClaimsSet().getStringClaim("organizationId");
        String warehouseId = signedJWT.getJWTClaimsSet().getStringClaim("warehouseId");

        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .header("X-User-Email", email)
                .header("X-User-Role", role);
        if (organizationId != null) {
            requestBuilder.header("X-Organization-Id", organizationId);
        }
        if (warehouseId != null) {
            requestBuilder.header("X-Warehouse-Id", warehouseId);
        }

        log.debug("Authenticated user: {} (role: {}) for path: {}", email, role, path);
        return requestBuilder.build();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private boolean isExcludedPath(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    private RSAPublicKey getPublicKey() throws Exception {
        String cachedPem = redisTemplate.opsForValue().get(REDIS_KEY).block();
        if (cachedPem != null && !cachedPem.isEmpty()) {
            return loadPublicKeyFromPEM(cachedPem);
        }

        if (publicKeyString != null && !publicKeyString.isEmpty() && !publicKeyString.contains("yourpublickey")) {
            try {
                RSAPublicKey key = loadPublicKeyFromPEM(publicKeyString);
                redisTemplate.opsForValue().set(REDIS_KEY, publicKeyString, CACHE_TTL).block();
                log.info("Loaded public key from configuration and cached in Redis");
                return key;
            } catch (Exception e) {
                log.warn("Failed to load public key from configuration: {}", e.getMessage());
            }
        }

        try {
            String publicKeyPEM = fetchPublicKeyFromSSOService();
            RSAPublicKey key = loadPublicKeyFromPEM(publicKeyPEM);
            redisTemplate.opsForValue().set(REDIS_KEY, publicKeyPEM, CACHE_TTL).block();
            log.info("Fetched public key from SSO Service and cached in Redis (TTL {})", CACHE_TTL);
            return key;
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
