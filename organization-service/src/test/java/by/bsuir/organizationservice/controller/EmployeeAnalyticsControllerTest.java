package by.bsuir.organizationservice.controller;

import by.bsuir.organizationservice.service.EmployeeAnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmployeeAnalyticsController Unit Tests")
class EmployeeAnalyticsControllerTest {

    @Mock
    private EmployeeAnalyticsService employeeAnalyticsService;

    @InjectMocks
    private EmployeeAnalyticsController employeeAnalyticsController;

    @Test
    @DisplayName("getEmployeesAnalytics: Should return analytics for all employees")
    void getEmployeesAnalytics_ShouldReturnAnalyticsForAllEmployees() {
        UUID orgId = UUID.randomUUID();
        List<Map<String, Object>> expectedAnalytics = List.of(
                Map.of("userId", UUID.randomUUID().toString(), "role", "WORKER", "daysWorked", 30),
                Map.of("userId", UUID.randomUUID().toString(), "role", "ACCOUNTANT", "daysWorked", 60)
        );

        when(employeeAnalyticsService.getEmployeesAnalytics(orgId)).thenReturn(expectedAnalytics);

        ResponseEntity<List<Map<String, Object>>> response = employeeAnalyticsController.getEmployeesAnalytics(orgId, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        verify(employeeAnalyticsService).getEmployeesAnalytics(orgId);
    }

    @Test
    @DisplayName("getEmployeeAnalytics: Should return detailed analytics for specific employee")
    void getEmployeeAnalytics_ShouldReturnDetailedAnalyticsForSpecificEmployee() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Map<String, Object> expectedAnalytics = Map.of(
                "userId", userId.toString(),
                "orgId", orgId.toString(),
                "role", "WORKER",
                "daysWorked", 45,
                "monthsWorked", 1,
                "avgOperationsPerDay", 15.5
        );

        when(employeeAnalyticsService.getEmployeeAnalytics(orgId, userId)).thenReturn(expectedAnalytics);

        ResponseEntity<Map<String, Object>> response = employeeAnalyticsController.getEmployeeAnalytics(orgId, userId, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKeys("userId", "orgId", "role", "daysWorked");
        verify(employeeAnalyticsService).getEmployeeAnalytics(orgId, userId);
    }
}

