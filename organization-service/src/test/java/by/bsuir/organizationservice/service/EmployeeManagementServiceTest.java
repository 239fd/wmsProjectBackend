package by.bsuir.organizationservice.service;

import by.bsuir.organizationservice.dto.AddEmployeeRequest;
import by.bsuir.organizationservice.dto.EmployeeResponse;
import by.bsuir.organizationservice.exception.AppException;
import by.bsuir.organizationservice.model.entity.OrganizationEmployee;
import by.bsuir.organizationservice.model.entity.OrganizationReadModel;
import by.bsuir.organizationservice.model.enums.OrganizationStatus;
import by.bsuir.organizationservice.repository.OrganizationEmployeeRepository;
import by.bsuir.organizationservice.repository.OrganizationReadModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmployeeManagementService Unit Tests")
class EmployeeManagementServiceTest {

    @Mock
    private OrganizationEmployeeRepository employeeRepository;

    @Mock
    private OrganizationReadModelRepository organizationRepository;

    @InjectMocks
    private EmployeeManagementService employeeManagementService;

    @Test
    @DisplayName("addEmployee: Given valid request Should add employee and return response")
    void addEmployee_GivenValidRequest_ShouldAddEmployeeAndReturnResponse() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AddEmployeeRequest request = new AddEmployeeRequest(userId, "WORKER");

        OrganizationReadModel org = OrganizationReadModel.builder()
                .orgId(orgId)
                .name("ООО Тест")
                .status(OrganizationStatus.ACTIVE)
                .build();

        when(organizationRepository.findByOrgId(orgId)).thenReturn(Optional.of(org));
        when(employeeRepository.existsByUserIdAndOrgIdAndIsActiveTrue(userId, orgId)).thenReturn(false);
        when(employeeRepository.save(any(OrganizationEmployee.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmployeeResponse response = employeeManagementService.addEmployee(orgId, request);

        assertThat(response).isNotNull();
        verify(organizationRepository).findByOrgId(orgId);
        verify(employeeRepository).existsByUserIdAndOrgIdAndIsActiveTrue(userId, orgId);
        verify(employeeRepository).save(any(OrganizationEmployee.class));
    }

    @Test
    @DisplayName("addEmployee: Given non-existing organization Should throw not found exception")
    void addEmployee_GivenNonExistingOrganization_ShouldThrowNotFoundException() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AddEmployeeRequest request = new AddEmployeeRequest(userId, "WORKER");

        when(organizationRepository.findByOrgId(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeManagementService.addEmployee(orgId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Организация не найдена");

        verify(organizationRepository).findByOrgId(orgId);
        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("addEmployee: Given already existing employee Should throw conflict exception")
    void addEmployee_GivenAlreadyExistingEmployee_ShouldThrowConflictException() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AddEmployeeRequest request = new AddEmployeeRequest(userId, "WORKER");

        OrganizationReadModel org = OrganizationReadModel.builder()
                .orgId(orgId)
                .name("ООО Тест")
                .status(OrganizationStatus.ACTIVE)
                .build();

        when(organizationRepository.findByOrgId(orgId)).thenReturn(Optional.of(org));
        when(employeeRepository.existsByUserIdAndOrgIdAndIsActiveTrue(userId, orgId)).thenReturn(true);

        assertThatThrownBy(() -> employeeManagementService.addEmployee(orgId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("уже состоит в организации");

        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("removeEmployee: Given existing employee Should deactivate employee")
    void removeEmployee_GivenExistingEmployee_ShouldDeactivateEmployee() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        OrganizationEmployee employee = OrganizationEmployee.builder()
                .userId(userId)
                .orgId(orgId)
                .role("WORKER")
                .joinedAt(LocalDateTime.now())
                .isActive(true)
                .build();

        when(employeeRepository.findByUserIdAndOrgIdAndIsActiveTrue(userId, orgId))
                .thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(OrganizationEmployee.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        employeeManagementService.removeEmployee(orgId, userId);

        assertThat(employee.getIsActive()).isFalse();
        assertThat(employee.getRemovedAt()).isNotNull();
        verify(employeeRepository).save(employee);
    }

    @Test
    @DisplayName("removeEmployee: Given non-existing employee Should throw not found exception")
    void removeEmployee_GivenNonExistingEmployee_ShouldThrowNotFoundException() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(employeeRepository.findByUserIdAndOrgIdAndIsActiveTrue(userId, orgId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeManagementService.removeEmployee(orgId, userId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найден в организации");

        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("getOrganizationEmployees: Should return list of employees")
    void getOrganizationEmployees_ShouldReturnListOfEmployees() {
        UUID orgId = UUID.randomUUID();

        OrganizationEmployee emp1 = OrganizationEmployee.builder()
                .userId(UUID.randomUUID())
                .orgId(orgId)
                .role("WORKER")
                .isActive(true)
                .build();

        OrganizationEmployee emp2 = OrganizationEmployee.builder()
                .userId(UUID.randomUUID())
                .orgId(orgId)
                .role("ACCOUNTANT")
                .isActive(true)
                .build();

        when(employeeRepository.findByOrgIdAndIsActiveTrue(orgId))
                .thenReturn(List.of(emp1, emp2));

        List<EmployeeResponse> responses = employeeManagementService.getOrganizationEmployees(orgId);

        assertThat(responses).hasSize(2);
        verify(employeeRepository).findByOrgIdAndIsActiveTrue(orgId);
    }

    @Test
    @DisplayName("getOrganizationEmployees: Given no employees Should return empty list")
    void getOrganizationEmployees_GivenNoEmployees_ShouldReturnEmptyList() {
        UUID orgId = UUID.randomUUID();

        when(employeeRepository.findByOrgIdAndIsActiveTrue(orgId)).thenReturn(List.of());

        List<EmployeeResponse> responses = employeeManagementService.getOrganizationEmployees(orgId);

        assertThat(responses).isEmpty();
        verify(employeeRepository).findByOrgIdAndIsActiveTrue(orgId);
    }
}

