package by.bsuir.organizationservice.service;

import by.bsuir.organizationservice.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventListener {

    private final OrganizationService organizationService;

    @RabbitListener(queues = RabbitMQConfig.DIRECTOR_DELETED_ORG_QUEUE)
    public void handleDirectorDeleted(Map<String, Object> event) {
        try {
            String userIdStr = (String) event.get("userId");
            String orgIdStr = (String) event.get("orgId");

            if (userIdStr == null || orgIdStr == null) {
                log.warn("user.director.deleted: некорректное событие: {}", event);
                return;
            }

            UUID userId = UUID.fromString(userIdStr);
            UUID orgId = UUID.fromString(orgIdStr);

            log.info("user.director.deleted: архивирую организацию {} (директор {})", orgId, userId);
            organizationService.archiveOrganizationOnDirectorDelete(orgId, userId);
        } catch (Exception e) {
            log.error("user.director.deleted: ошибка обработки: {}", e.getMessage(), e);
        }
    }
}
