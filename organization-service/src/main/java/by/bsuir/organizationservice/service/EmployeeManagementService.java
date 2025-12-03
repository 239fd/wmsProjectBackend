package by.bsuir.organizationservice.service;

import by.bsuir.organizationservice.dto.AddEmployeeRequest;
import by.bsuir.organizationservice.dto.EmployeeResponse;
import by.bsuir.organizationservice.exception.AppException;
import by.bsuir.organizationservice.model.entity.OrganizationEmployee;
import by.bsuir.organizationservice.model.entity.OrganizationReadModel;
import by.bsuir.organizationservice.repository.OrganizationEmployeeRepository;
import by.bsuir.organizationservice.repository.OrganizationReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeManagementService {

    private final OrganizationEmployeeRepository employeeRepository;
    private final OrganizationReadModelRepository organizationRepository;
    private final RestTemplate restTemplate = new RestTemplate();

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

    @Transactional(readOnly = true)
    public List<EmployeeResponse> getOrganizationEmployees(UUID orgId) {
        log.info("Getting employees for organization {}", orgId);

        List<OrganizationEmployee> employees = employeeRepository.findByOrgIdAndIsActiveTrue(orgId);

        return employees.stream()
                .map(emp -> {
                    Map<String, Object> userInfo = getUserInfo(emp.getUserId());
                    return mapToEmployeeResponse(emp, userInfo);
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> getUserInfo(UUID userId) {
        try {
            String url = "http://localhost:8000/api/profile/" + userId;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("Could not fetch user info for {}: {}", userId, e.getMessage());
            return Map.of(
                    "userId", userId.toString(),
                    "username", "Unknown User",
                    "email", "unknown@example.com"
            );
        }
    }

    private EmployeeResponse mapToEmployeeResponse(OrganizationEmployee employee, Map<String, Object> userInfo) {
        return EmployeeResponse.builder()
                .userId(employee.getUserId())
                .orgId(employee.getOrgId())
                .username((String) userInfo.getOrDefault("username", "Unknown"))
                .email((String) userInfo.getOrDefault("email", "unknown@example.com"))
                .role(employee.getRole())
                .joinedAt(employee.getJoinedAt())
                .build();
    }
}

