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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InternalEmployeeController Tests")
class InternalEmployeeControllerTest {

    @Mock
    private EmployeeManagementService employeeManagementService;

    @InjectMocks
    private InternalEmployeeController controller;

    @Test
    @DisplayName("addEmployee: inter-service вызов → 201 Created (без проверки роли)")
    void addEmployee_whenCalled_thenReturns201() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AddEmployeeRequest req = new AddEmployeeRequest(userId, "WORKER");
        EmployeeResponse expected = EmployeeResponse.builder()
                .userId(userId).orgId(orgId).role("WORKER").isActive(true).build();
        when(employeeManagementService.addEmployee(orgId, req)).thenReturn(expected);

        ResponseEntity<EmployeeResponse> response = controller.addEmployee(orgId, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getRole()).isEqualTo("WORKER");
    }
}
