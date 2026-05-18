package by.bsuir.organizationservice.service;

import by.bsuir.organizationservice.model.entity.OrganizationEmployee;
import by.bsuir.organizationservice.repository.OrganizationEmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeAnalyticsService {

    private final OrganizationEmployeeRepository employeeRepository;

    @Cacheable(value = "employeesAnalytics", key = "#orgId")
    public List<Map<String, Object>> getEmployeesAnalytics(UUID orgId) {
        log.info("Calculating analytics for employees in organization: {}", orgId);

        List<OrganizationEmployee> employees = employeeRepository.findByOrgIdAndIsActiveTrue(orgId);

        return employees.stream()
                .map(emp -> {
                    Map<String, Object> analytics = new HashMap<>();
                    analytics.put("userId", emp.getUserId());
                    analytics.put("role", emp.getRole());
                    analytics.put("joinedAt", emp.getJoinedAt());

                    long daysWorked = ChronoUnit.DAYS.between(emp.getJoinedAt(), LocalDateTime.now());
                    analytics.put("daysWorked", daysWorked);
                    return analytics;
                })
                .collect(Collectors.toList());
    }

    @Cacheable(value = "employeeAnalytics", key = "#orgId + '-' + #userId")
    public Map<String, Object> getEmployeeAnalytics(UUID orgId, UUID userId) {
        log.info("Calculating analytics for employee {} in organization {}", userId, orgId);

        OrganizationEmployee employee = employeeRepository
                .findByUserIdAndOrgIdAndIsActiveTrue(userId, orgId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("userId", employee.getUserId());
        analytics.put("orgId", employee.getOrgId());
        analytics.put("role", employee.getRole());
        analytics.put("joinedAt", employee.getJoinedAt());

        long daysWorked = ChronoUnit.DAYS.between(employee.getJoinedAt(), LocalDateTime.now());
        analytics.put("daysWorked", daysWorked);
        analytics.put("monthsWorked", daysWorked / 30);

        return analytics;
    }
}
