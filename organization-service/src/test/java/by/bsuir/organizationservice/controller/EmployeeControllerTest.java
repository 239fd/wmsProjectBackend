package by.bsuir.organizationservice.controller;

import by.bsuir.organizationservice.dto.AddEmployeeRequest;
import by.bsuir.organizationservice.dto.EmployeeResponse;
import by.bsuir.organizationservice.service.EmployeeManagementService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmployeeController Unit Tests")
class EmployeeControllerTest {

    @Mock
    private EmployeeManagementService employeeManagementService;

    @InjectMocks
    private EmployeeController employeeController;

    @Test
    @DisplayName("addEmployee: Given DIRECTOR role Should add employee and return 201")
    void addEmployee_GivenDirectorRole_ShouldAddEmployeeAndReturn201() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AddEmployeeRequest request = new AddEmployeeRequest(userId, "WORKER");

        EmployeeResponse expectedResponse = EmployeeResponse.builder()
                .userId(userId)
                .orgId(orgId)
                .username("Test User")
                .email("test@example.com")
                .role("WORKER")
                .joinedAt(java.time.LocalDateTime.now())
                .build();

        when(employeeManagementService.addEmployee(eq(orgId), any(AddEmployeeRequest.class)))
                .thenReturn(expectedResponse);

        ResponseEntity<EmployeeResponse> response = employeeController.addEmployee(orgId, request, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expectedResponse);
        verify(employeeManagementService).addEmployee(orgId, request);
    }

    @Test
    @DisplayName("addEmployee: Given non-DIRECTOR role Should return 403")
    void addEmployee_GivenNonDirectorRole_ShouldReturn403() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AddEmployeeRequest request = new AddEmployeeRequest(userId, "WORKER");

        ResponseEntity<EmployeeResponse> response = employeeController.addEmployee(orgId, request, "WORKER");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNull();
        verify(employeeManagementService, never()).addEmployee(any(), any());
    }

    @Test
    @DisplayName("addEmployee: Given null role Should return 403")
    void addEmployee_GivenNullRole_ShouldReturn403() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AddEmployeeRequest request = new AddEmployeeRequest(userId, "WORKER");

        ResponseEntity<EmployeeResponse> response = employeeController.addEmployee(orgId, request, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(employeeManagementService, never()).addEmployee(any(), any());
    }

    @Test
    @DisplayName("removeEmployee: Given DIRECTOR role Should remove employee and return 200")
    void removeEmployee_GivenDirectorRole_ShouldRemoveEmployeeAndReturn200() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        doNothing().when(employeeManagementService).removeEmployee(orgId, userId);

        ResponseEntity<Map<String, String>> response = employeeController.removeEmployee(orgId, userId, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("message", "Сотрудник удален из организации");
        verify(employeeManagementService).removeEmployee(orgId, userId);
    }

    @Test
    @DisplayName("removeEmployee: Given non-DIRECTOR role Should return 403")
    void removeEmployee_GivenNonDirectorRole_ShouldReturn403() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ResponseEntity<Map<String, String>> response = employeeController.removeEmployee(orgId, userId, "ACCOUNTANT");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(employeeManagementService, never()).removeEmployee(any(), any());
    }

    @Test
    @DisplayName("getOrganizationEmployees: Should return list of employees")
    void getOrganizationEmployees_ShouldReturnListOfEmployees() {
        UUID orgId = UUID.randomUUID();

        EmployeeResponse emp1 = EmployeeResponse.builder()
                .userId(UUID.randomUUID())
                .orgId(orgId)
                .username("User 1")
                .email("user1@example.com")
                .role("WORKER")
                .joinedAt(java.time.LocalDateTime.now())
                .build();

        EmployeeResponse emp2 = EmployeeResponse.builder()
                .userId(UUID.randomUUID())
                .orgId(orgId)
                .username("User 2")
                .email("user2@example.com")
                .role("ACCOUNTANT")
                .joinedAt(java.time.LocalDateTime.now())
                .build();

        List<EmployeeResponse> expectedList = List.of(emp1, emp2);

        when(employeeManagementService.getOrganizationEmployees(orgId)).thenReturn(expectedList);

        ResponseEntity<List<EmployeeResponse>> response = employeeController.getOrganizationEmployees(orgId, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        verify(employeeManagementService).getOrganizationEmployees(orgId);
    }
}

