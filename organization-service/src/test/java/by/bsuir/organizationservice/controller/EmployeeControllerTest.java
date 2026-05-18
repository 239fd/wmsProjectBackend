package by.bsuir.organizationservice.controller;

import by.bsuir.organizationservice.dto.AddEmployeeRequest;
import by.bsuir.organizationservice.dto.EmployeeResponse;
import by.bsuir.organizationservice.dto.UpdateEmployeeStatusRequest;
import by.bsuir.organizationservice.service.EmployeeManagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmployeeController Tests")
class EmployeeControllerTest {

    @Mock
    private EmployeeManagementService employeeManagementService;

    @InjectMocks
    private EmployeeController controller;

    private EmployeeResponse sample() {
        return EmployeeResponse.builder()
                .userId(UUID.randomUUID()).orgId(UUID.randomUUID())
                .username("Ivanov").email("i@i.by").role("WORKER")
                .isActive(true).isBlocked(false).build();
    }

    @Test
    @DisplayName("addEmployee: DIRECTOR → 201 Created")
    void addEmployee_givenDirectorRole_whenCalled_thenReturns201() {
        UUID orgId = UUID.randomUUID();
        AddEmployeeRequest req = new AddEmployeeRequest(UUID.randomUUID(), "WORKER");
        when(employeeManagementService.addEmployee(orgId, req)).thenReturn(sample());

        assertThat(controller.addEmployee(orgId, req, "DIRECTOR").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("addEmployee: WORKER → 403")
    void addEmployee_givenWorkerRole_whenCalled_thenForbidden() {
        AddEmployeeRequest req = new AddEmployeeRequest(UUID.randomUUID(), "WORKER");
        assertThat(controller.addEmployee(UUID.randomUUID(), req, "WORKER").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("removeEmployee: DIRECTOR → 200 OK с message")
    void removeEmployee_givenDirectorRole_whenCalled_thenReturns200() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ResponseEntity<Map<String, String>> response = controller.removeEmployee(orgId, userId, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("message");
        verify(employeeManagementService).removeEmployee(orgId, userId);
    }

    @Test
    @DisplayName("removeEmployee: WORKER → 403")
    void removeEmployee_givenWorkerRole_whenCalled_thenForbidden() {
        assertThat(controller.removeEmployee(UUID.randomUUID(), UUID.randomUUID(), "WORKER")
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("updateEmployeeStatus: blocked=true → blockEmployee + 200 OK")
    void updateEmployeeStatus_givenBlockedTrue_whenCalled_thenBlocks() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UpdateEmployeeStatusRequest req = new UpdateEmployeeStatusRequest(true);

        ResponseEntity<Map<String, String>> response = controller.updateEmployeeStatus(
                orgId, userId, req, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(employeeManagementService).blockEmployee(orgId, userId);
    }

    @Test
    @DisplayName("updateEmployeeStatus: blocked=false → unblockEmployee + 200 OK")
    void updateEmployeeStatus_givenBlockedFalse_whenCalled_thenUnblocks() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UpdateEmployeeStatusRequest req = new UpdateEmployeeStatusRequest(false);

        controller.updateEmployeeStatus(orgId, userId, req, "DIRECTOR");

        verify(employeeManagementService).unblockEmployee(orgId, userId);
    }

    @Test
    @DisplayName("updateEmployeeStatus: WORKER → 403")
    void updateEmployeeStatus_givenWorkerRole_whenCalled_thenForbidden() {
        UpdateEmployeeStatusRequest req = new UpdateEmployeeStatusRequest(true);
        assertThat(controller.updateEmployeeStatus(UUID.randomUUID(), UUID.randomUUID(), req, "WORKER")
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("getOrganizationEmployees: WORKER → 200 OK (любая роль OK)")
    void getOrganizationEmployees_givenAnyRole_whenCalled_thenReturns200() {
        UUID orgId = UUID.randomUUID();
        when(employeeManagementService.getOrganizationEmployees(eq(orgId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample())));

        ResponseEntity<?> response = controller.getOrganizationEmployees(
                orgId, PageRequest.of(0, 20), "WORKER");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getOrganizationEmployees: null role → 403")
    void getOrganizationEmployees_givenNullRole_whenCalled_thenForbidden() {
        assertThat(controller.getOrganizationEmployees(UUID.randomUUID(), PageRequest.of(0, 20), null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
