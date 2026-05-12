package by.bsuir.organizationservice.service;

import by.bsuir.organizationservice.config.RabbitMQConfig;
import by.bsuir.organizationservice.dto.AddEmployeeRequest;
import by.bsuir.organizationservice.dto.EmployeeResponse;
import by.bsuir.organizationservice.exception.AppException;
import by.bsuir.organizationservice.model.entity.OrganizationEmployee;
import by.bsuir.organizationservice.model.entity.OrganizationReadModel;
import by.bsuir.organizationservice.repository.OrganizationEmployeeRepository;
import by.bsuir.organizationservice.repository.OrganizationReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeManagementService {

    private final OrganizationEmployeeRepository employeeRepository;
    private final OrganizationReadModelRepository organizationRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate restTemplate;

    @Transactional
    public EmployeeResponse addEmployee(UUID orgId, AddEmployeeRequest request) {
        log.info("Adding employee {} to organization {}", request.userId(), orgId);

        OrganizationReadModel organization = organizationRepository.findByOrgId(orgId)
                .orElseThrow(() -> AppException.notFound("Организация не найдена"));

        if (employeeRepository.existsByUserIdAndOrgIdAndIsActiveTrue(request.userId(), orgId)) {
            throw AppException.conflict("Сотрудник уже состоит в организации");
        }

        Map<String, Object> userInfo = getUserInfo(request.userId());
        if (userInfo == null) {
            throw AppException.notFound("Пользователь не найден в системе");
        }

        OrganizationEmployee employee = OrganizationEmployee.builder()
                .userId(request.userId())
                .orgId(orgId)
                .role(request.role())
                .joinedAt(LocalDateTime.now())
                .isActive(true)
                .build();

        employeeRepository.save(employee);

        return mapToEmployeeResponse(employee, userInfo);
    }

    @Transactional
    public void removeEmployee(UUID orgId, UUID userId) {
        log.info("Removing employee {} from organization {}", userId, orgId);

        OrganizationEmployee employee = employeeRepository
                .findByUserIdAndOrgIdAndIsActiveTrue(userId, orgId)
                .orElseThrow(() -> AppException.notFound("Сотрудник не найден в организации"));

        employee.setIsActive(false);
        employee.setRemovedAt(LocalDateTime.now());
        employeeRepository.save(employee);

        log.info("Employee {} removed from organization {}", userId, orgId);
    }

    @Transactional
    public void blockEmployee(UUID orgId, UUID userId) {
        log.info("Blocking employee {} in organization {}", userId, orgId);

        OrganizationEmployee employee = employeeRepository
                .findByUserIdAndOrgIdAndIsActiveTrue(userId, orgId)
                .orElseThrow(() -> AppException.notFound("Сотрудник не найден в организации"));

        if (Boolean.TRUE.equals(employee.getIsBlocked())) {
            throw AppException.conflict("Сотрудник уже заблокирован");
        }

        employee.setIsBlocked(true);
        employee.setBlockedAt(LocalDateTime.now());
        employeeRepository.save(employee);

        publishEmployeeStatusChanged(userId, orgId, true);
        log.info("Employee {} blocked in organization {}", userId, orgId);
    }

    @Transactional
    public void unblockEmployee(UUID orgId, UUID userId) {
        log.info("Unblocking employee {} in organization {}", userId, orgId);

        OrganizationEmployee employee = employeeRepository
                .findByUserIdAndOrgIdAndIsActiveTrue(userId, orgId)
                .orElseThrow(() -> AppException.notFound("Сотрудник не найден в организации"));

        if (!Boolean.TRUE.equals(employee.getIsBlocked())) {
            throw AppException.conflict("Сотрудник не заблокирован");
        }

        employee.setIsBlocked(false);
        employee.setBlockedAt(null);
        employeeRepository.save(employee);

        publishEmployeeStatusChanged(userId, orgId, false);
        log.info("Employee {} unblocked in organization {}", userId, orgId);
    }

    private void publishEmployeeStatusChanged(UUID userId, UUID orgId, boolean blocked) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", userId.toString());
            payload.put("orgId", orgId.toString());
            payload.put("blocked", blocked);
            payload.put("timestamp", LocalDateTime.now().toString());

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.ORGANIZATION_EXCHANGE,
                    RabbitMQConfig.EMPLOYEE_STATUS_CHANGED_KEY,
                    payload
            );
        } catch (Exception e) {
            log.error("Failed to publish employee.status.changed for user {}: {}", userId, e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> getOrganizationEmployees(UUID orgId, Pageable pageable) {
        log.info("Getting employees for organization {} (page={}, size={})",
                orgId, pageable.getPageNumber(), pageable.getPageSize());

        Page<OrganizationEmployee> page = employeeRepository.findByOrgIdAndIsActiveTrue(orgId, pageable);
        if (page.isEmpty()) {
            return page.map(emp -> mapToEmployeeResponse(emp, fallbackUser(emp.getUserId())));
        }

        Map<UUID, Map<String, Object>> usersById = lookupUsers(
                page.getContent().stream().map(OrganizationEmployee::getUserId).toList());

        return page.map(emp -> mapToEmployeeResponse(
                emp,
                usersById.getOrDefault(emp.getUserId(), fallbackUser(emp.getUserId()))));
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Map<String, Object>> lookupUsers(List<UUID> ids) {
        try {
            String url = "http://SSOSERVICE/api/internal/users/lookup";
            Map<String, Object> body = Map.of(
                    "ids", ids.stream().map(UUID::toString).toList());
            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
            Map<String, Map<String, Object>> raw = response.getBody();
            if (raw == null) {
                return Map.of();
            }
            Map<UUID, Map<String, Object>> result = new java.util.HashMap<>();
            raw.forEach((key, info) -> result.put(UUID.fromString(key), info));
            return result;
        } catch (Exception e) {
            log.warn("Bulk user lookup failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> getUserInfo(UUID userId) {
        try {
            String url = "http://SSOSERVICE/api/internal/users/" + userId;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("Could not fetch user info for {}: {}", userId, e.getMessage());
            return fallbackUser(userId);
        }
    }

    private Map<String, Object> fallbackUser(UUID userId) {
        return Map.of(
                "userId", userId.toString(),
                "username", "Unknown User",
                "email", "unknown@example.com"
        );
    }

    private EmployeeResponse mapToEmployeeResponse(OrganizationEmployee employee, Map<String, Object> userInfo) {
        return EmployeeResponse.builder()
                .userId(employee.getUserId())
                .orgId(employee.getOrgId())
                .username((String) userInfo.getOrDefault("username", "Unknown"))
                .email((String) userInfo.getOrDefault("email", "unknown@example.com"))
                .role(employee.getRole())
                .joinedAt(employee.getJoinedAt())
                .isActive(employee.getIsActive())
                .isBlocked(employee.getIsBlocked())
                .build();
    }
}

