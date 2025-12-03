package by.bsuir.organizationservice.service;

import by.bsuir.organizationservice.model.entity.OrganizationEmployee;
import by.bsuir.organizationservice.repository.OrganizationEmployeeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmployeeAnalyticsService Unit Tests")
class EmployeeAnalyticsServiceTest {

    @Mock
    private OrganizationEmployeeRepository employeeRepository;

    @InjectMocks
    private EmployeeAnalyticsService employeeAnalyticsService;

    @Test
    @DisplayName("getEmployeesAnalytics: Should return analytics for all employees")
    void getEmployeesAnalytics_ShouldReturnAnalyticsForAllEmployees() {
        UUID orgId = UUID.randomUUID();

        OrganizationEmployee emp1 = OrganizationEmployee.builder()
                .userId(UUID.randomUUID())
                .orgId(orgId)
                .role("WORKER")
                .joinedAt(LocalDateTime.now().minusDays(30))
                .isActive(true)
                .build();

        OrganizationEmployee emp2 = OrganizationEmployee.builder()
                .userId(UUID.randomUUID())
                .orgId(orgId)
                .role("ACCOUNTANT")
                .joinedAt(LocalDateTime.now().minusDays(60))
                .isActive(true)
                .build();

        when(employeeRepository.findByOrgIdAndIsActiveTrue(orgId))
                .thenReturn(List.of(emp1, emp2));

        List<Map<String, Object>> analytics = employeeAnalyticsService.getEmployeesAnalytics(orgId);

        assertThat(analytics).hasSize(2);
        assertThat(analytics.get(0)).containsKeys("userId", "role", "joinedAt", "daysWorked");
        assertThat(analytics.get(1)).containsKeys("userId", "role", "joinedAt", "daysWorked");
        verify(employeeRepository).findByOrgIdAndIsActiveTrue(orgId);
    }

    @Test
    @DisplayName("getEmployeesAnalytics: Given no employees Should return empty list")
    void getEmployeesAnalytics_GivenNoEmployees_ShouldReturnEmptyList() {
        UUID orgId = UUID.randomUUID();

        when(employeeRepository.findByOrgIdAndIsActiveTrue(orgId)).thenReturn(List.of());

        List<Map<String, Object>> analytics = employeeAnalyticsService.getEmployeesAnalytics(orgId);

        assertThat(analytics).isEmpty();
        verify(employeeRepository).findByOrgIdAndIsActiveTrue(orgId);
    }

    @Test
    @DisplayName("getEmployeeAnalytics: Given existing employee Should return detailed analytics")
    void getEmployeeAnalytics_GivenExistingEmployee_ShouldReturnDetailedAnalytics() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        OrganizationEmployee employee = OrganizationEmployee.builder()
                .userId(userId)
                .orgId(orgId)
                .role("WORKER")
                .joinedAt(LocalDateTime.now().minusDays(90))
                .isActive(true)
                .build();

        when(employeeRepository.findByUserIdAndOrgIdAndIsActiveTrue(userId, orgId))
                .thenReturn(Optional.of(employee));

        Map<String, Object> analytics = employeeAnalyticsService.getEmployeeAnalytics(orgId, userId);

        assertThat(analytics).isNotNull();
        assertThat(analytics).containsKeys("userId", "orgId", "role", "joinedAt", "daysWorked", "monthsWorked");
        assertThat(analytics.get("userId")).isEqualTo(userId);
        assertThat(analytics.get("orgId")).isEqualTo(orgId);
        assertThat(analytics.get("role")).isEqualTo("WORKER");

        verify(employeeRepository).findByUserIdAndOrgIdAndIsActiveTrue(userId, orgId);
    }

    @Test
    @DisplayName("getEmployeeAnalytics: Given non-existing employee Should throw exception")
    void getEmployeeAnalytics_GivenNonExistingEmployee_ShouldThrowException() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(employeeRepository.findByUserIdAndOrgIdAndIsActiveTrue(userId, orgId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeAnalyticsService.getEmployeeAnalytics(orgId, userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Employee not found");

        verify(employeeRepository).findByUserIdAndOrgIdAndIsActiveTrue(userId, orgId);
    }

    @Test
    @DisplayName("getEmployeeAnalytics: Should calculate daysWorked correctly")
    void getEmployeeAnalytics_ShouldCalculateDaysWorkedCorrectly() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        OrganizationEmployee employee = OrganizationEmployee.builder()
                .userId(userId)
                .orgId(orgId)
                .role("WORKER")
                .joinedAt(LocalDateTime.now().minusDays(45))
                .isActive(true)
                .build();

        when(employeeRepository.findByUserIdAndOrgIdAndIsActiveTrue(userId, orgId))
                .thenReturn(Optional.of(employee));

        Map<String, Object> analytics = employeeAnalyticsService.getEmployeeAnalytics(orgId, userId);

        long daysWorked = (long) analytics.get("daysWorked");
        assertThat(daysWorked).isGreaterThanOrEqualTo(44).isLessThanOrEqualTo(46);

        long monthsWorked = (long) analytics.get("monthsWorked");
        assertThat(monthsWorked).isEqualTo(1);
    }
}

