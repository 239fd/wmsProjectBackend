package by.bsuir.productservice.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class SecurityUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SecurityUtils() { }

    public static String resolveRole(String headerValue) {
        return headerValue != null ? headerValue : claim("role");
    }

    public static UUID resolveUserId(UUID headerValue) {
        if (headerValue != null) return headerValue;
        String sub = claim("sub");
        if (sub == null) return null;
        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static UUID resolveOrgId(UUID headerValue) {
        if (headerValue != null) return headerValue;
        String org = claim("organizationId");
        if (org == null) return null;
        try {
            return UUID.fromString(org);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String currentRole() {
        return claim("role");
    }

    private static String claim(String name) {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest request = attrs.getRequest();
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String[] parts = auth.substring(7).split("\\.");
            if (parts.length < 2) return null;
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode node = MAPPER.readTree(new String(payload, StandardCharsets.UTF_8));
            JsonNode v = node.get(name);
            return v != null && !v.isNull() ? v.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
