package by.bsuir.productservice.config.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Organization-Id";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UUID orgId = parseHeader(request.getHeader(HEADER));
        if (orgId == null) {
            orgId = parseJwtClaim(request.getHeader("Authorization"));
        }
        if (orgId != null) {
            TenantContext.set(orgId);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }

    private UUID parseHeader(String header) {
        if (header == null || header.isBlank()) return null;
        try {
            return UUID.fromString(header);
        } catch (IllegalArgumentException e) {
            log.warn("Некорректный X-Organization-Id заголовок: {}", header);
            return null;
        }
    }

    private UUID parseJwtClaim(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        try {
            String[] parts = authHeader.substring(7).split("\\.");
            if (parts.length < 2) return null;
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode node = MAPPER.readTree(new String(payload, StandardCharsets.UTF_8));
            JsonNode v = node.get("organizationId");
            if (v == null || v.isNull()) return null;
            return UUID.fromString(v.asText());
        } catch (Exception e) {
            return null;
        }
    }
}
