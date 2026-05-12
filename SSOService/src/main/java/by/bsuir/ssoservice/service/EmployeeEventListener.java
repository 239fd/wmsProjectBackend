package by.bsuir.ssoservice.service;

import by.bsuir.ssoservice.config.RabbitMQConfig;
import by.bsuir.ssoservice.model.entity.UserReadModel;
import by.bsuir.ssoservice.model.enums.UserRole;
import by.bsuir.ssoservice.repository.LoginAuditRepository;
import by.bsuir.ssoservice.repository.UserReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeEventListener {

    private final UserReadModelRepository userRepository;
    private final LoginAuditRepository loginAuditRepository;
    private final RefreshTokenService refreshTokenService;

    @RabbitListener(queues = RabbitMQConfig.EMPLOYEE_STATUS_CHANGED_QUEUE)
    @Transactional
    public void handleEmployeeStatusChanged(Map<String, Object> event) {
        try {
            String userIdStr = (String) event.get("userId");
            Boolean blocked = (Boolean) event.get("blocked");

            if (userIdStr == null || blocked == null) {
                log.warn("EmployeeEventListener: получено некорректное событие: {}", event);
                return;
            }

            UUID userId = UUID.fromString(userIdStr);
            userRepository.findById(userId).ifPresent(user -> {
                user.setIsActive(!blocked);
                user.setUpdatedAt(LocalDateTime.now());
                userRepository.save(user);
                if (blocked) {
                    loginAuditRepository.deactivateAllUserSessions(userId);
                    refreshTokenService.deleteAllUserTokens(userId);
                }
                log.info("EmployeeEventListener: пользователь {} {}",
                        userId, blocked ? "заблокирован" : "разблокирован");
            });
        } catch (Exception e) {
            log.error("EmployeeEventListener: ошибка обработки события: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.ORGANIZATION_ARCHIVED_SSO_QUEUE)
    @Transactional
    public void handleOrganizationArchived(Map<String, Object> event) {
        try {
            String orgIdStr = (String) event.get("orgId");
            if (orgIdStr == null) {
                log.warn("organization.archived: некорректное событие: {}", event);
                return;
            }
            UUID orgId = UUID.fromString(orgIdStr);
            List<UserReadModel> users = userRepository.findByOrganizationId(orgId);
            int affected = 0;
            for (UserReadModel user : users) {
                if (user.getRole() == UserRole.DIRECTOR) {
                    continue;
                }
                user.setIsActive(false);
                user.setOrganizationId(null);
                user.setWarehouseId(null);
                user.setUpdatedAt(LocalDateTime.now());
                userRepository.save(user);
                loginAuditRepository.deactivateAllUserSessions(user.getUserId());
                refreshTokenService.deleteAllUserTokens(user.getUserId());
                affected++;
            }
            log.info("organization.archived: уволено {} пользователей в организации {}", affected, orgId);
        } catch (Exception e) {
            log.error("organization.archived: ошибка обработки: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.WAREHOUSE_DELETED_SSO_QUEUE)
    @Transactional
    public void handleWarehouseDeleted(Map<String, Object> event) {
        try {
            String warehouseIdStr = (String) event.get("warehouseId");
            if (warehouseIdStr == null) {
                log.warn("warehouse.deleted: некорректное событие: {}", event);
                return;
            }
            UUID warehouseId = UUID.fromString(warehouseIdStr);
            List<UserReadModel> users = userRepository.findByWarehouseId(warehouseId);
            int deleted = 0;
            for (UserReadModel user : users) {
                if (user.getRole() == UserRole.DIRECTOR) {
                    continue;
                }
                loginAuditRepository.deactivateAllUserSessions(user.getUserId());
                refreshTokenService.deleteAllUserTokens(user.getUserId());
                userRepository.delete(user);
                deleted++;
            }
            log.info("warehouse.deleted: удалено {} сотрудников склада {}", deleted, warehouseId);
        } catch (Exception e) {
            log.error("warehouse.deleted: ошибка обработки: {}", e.getMessage(), e);
        }
    }
}