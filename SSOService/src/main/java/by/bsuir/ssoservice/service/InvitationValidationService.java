package by.bsuir.ssoservice.service;

import by.bsuir.ssoservice.dto.response.InvitationValidationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class InvitationValidationService {

    private final RestTemplate restTemplate;

    public InvitationValidationService(@Qualifier("loadBalancedRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public InvitationValidationResponse validateInvitation(UUID invitationToken) {
        try {
            String url = "http://ORGANIZATION-SERVICE/api/invitations/validate?token=" + invitationToken;

            Map<String, Object> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            ).getBody();

            if (response == null) {
                return new InvitationValidationResponse(false, null, null, null, null, null, "Ошибка валидации");
            }

            Boolean valid = (Boolean) response.get("valid");
            if (!valid) {
                return new InvitationValidationResponse(
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        (String) response.get("message")
                );
            }

            return new InvitationValidationResponse(
                    true,
                    (String) response.get("organizationName"),
                    (String) response.get("role"),
                    (String) response.get("email"),
                    response.get("organizationId") != null ? UUID.fromString((String) response.get("organizationId")) : null,
                    response.get("warehouseId") != null ? UUID.fromString((String) response.get("warehouseId")) : null,
                    (String) response.get("message")
            );
        } catch (Exception e) {
            log.error("Failed to validate invitation: {}", e.getMessage(), e);
            return new InvitationValidationResponse(false, null, null, null, null, null, "Ошибка связи с сервисом организаций");
        }
    }

    public void markInvitationAsUsed(UUID invitationToken, UUID userId) {
        try {
            String url = "http://ORGANIZATION-SERVICE/api/invitations/" + invitationToken + "/mark-used";
            restTemplate.postForObject(url, Map.of("userId", userId.toString()), Void.class);
            log.info("Invitation marked as used: {}", invitationToken);
        } catch (Exception e) {
            log.error("Failed to mark invitation as used: {}", e.getMessage(), e);
        }
    }

    public void addEmployeeToOrganization(UUID orgId, UUID userId, String role) {
        String url = "http://ORGANIZATION-SERVICE/api/internal/organizations/" + orgId + "/employees";
        Map<String, Object> body = Map.of(
                "userId", userId.toString(),
                "role", role
        );
        try {
            restTemplate.postForObject(url, body, Map.class);
            log.info("Employee {} added to organization {} (role={})", userId, orgId, role);
        } catch (Exception e) {
            log.error("Failed to add employee {} to organization {}: {}", userId, orgId, e.getMessage(), e);
            throw new RuntimeException("Не удалось добавить сотрудника в организацию: " + e.getMessage(), e);
        }
    }
}