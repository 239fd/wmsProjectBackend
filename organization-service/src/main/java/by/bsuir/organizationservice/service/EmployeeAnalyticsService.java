package by.bsuir.organizationservice.service;

import by.bsuir.organizationservice.model.entity.OrganizationEmployee;
import by.bsuir.organizationservice.repository.OrganizationEmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeAnalyticsService {

    private final OrganizationEmployeeRepository employeeRepository;
    private final RestTemplate restTemplate = new RestTemplate();

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

                    try {
                        Map<String, Object> operationsStats = getEmployeeOperationsStats(emp.getUserId());
                        analytics.put("operationsStats", operationsStats);
                    } catch (Exception e) {
                        log.warn("Could not fetch operations stats for user {}: {}", emp.getUserId(), e.getMessage());
                        analytics.put("operationsStats", Map.of("available", false));
                    }

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

        try {
            Map<String, Object> operationsStats = getEmployeeOperationsStats(userId);
            analytics.put("operationsStats", operationsStats);

            if (operationsStats.containsKey("totalOperations")) {
                int totalOps = (int) operationsStats.get("totalOperations");
                double avgOpsPerDay = daysWorked > 0 ? (double) totalOps / daysWorked : 0;
                analytics.put("avgOperationsPerDay", Math.round(avgOpsPerDay * 100.0) / 100.0);
                analytics.put("performanceRating", calculatePerformanceRating(avgOpsPerDay));
            }
        } catch (Exception e) {
            log.warn("Could not calculate detailed stats for user {}: {}", userId, e.getMessage());
        }

        return analytics;
    }

    private Map<String, Object> getEmployeeOperationsStats(UUID userId) {
        try {
            String url = "http://localhost:8030/api/operations/user/" + userId + "/stats";
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            log.debug("Operations stats not available for user {}", userId);
            return Map.of(
                    "totalOperations", 0,
                    "available", false
            );
        }
    }

    private String calculatePerformanceRating(double avgOpsPerDay) {
        if (avgOpsPerDay >= 20) return "EXCELLENT";
        if (avgOpsPerDay >= 15) return "GOOD";
        if (avgOpsPerDay >= 10) return "AVERAGE";
        if (avgOpsPerDay >= 5) return "BELOW_AVERAGE";
        return "LOW";
    }
}

