package by.bsuir.organizationservice.service;

import by.bsuir.organizationservice.config.RabbitMQConfig;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmployeeManagementService — модульные тесты")
class EmployeeManagementServiceTest {

    @Mock private OrganizationEmployeeRepository employeeRepository;
    @Mock private OrganizationReadModelRepository organizationRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private RestTemplate restTemplate;

    @InjectMocks private EmployeeManagementService service;

    @Test
    @DisplayName("addEmployee: новый сотрудник → сохраняется, маппится по данным из SSO")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void addEmployee_GivenValid_ShouldPersistAndMap() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OrganizationReadModel rm = OrganizationReadModel.builder()
                .orgId(orgId).name("ООО Ромашка").unp("123456789")
                .status(OrganizationStatus.ACTIVE).build();
        when(organizationRepository.findByOrgId(orgId)).thenReturn(Optional.of(rm));
        when(employeeRepository.existsByUserIdAndOrgIdAndIsActiveTrue(userId, orgId)).thenReturn(false);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", userId.toString());
        userInfo.put("username", "ivan");
        userInfo.put("email", "ivan@example.com");
        when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(userInfo));

        EmployeeResponse response = service.addEmployee(orgId, new AddEmployeeRequest(userId, "WORKER"));

        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getRole()).isEqualTo("WORKER");
        assertThat(response.getUsername()).isEqualTo("ivan");
        assertThat(response.getEmail()).isEqualTo("ivan@example.com");
        verify(employeeRepository).save(any(OrganizationEmployee.class));
    }

    @Test
    @DisplayName("addEmployee: организация не найдена → 404 not found")
    void addEmployee_GivenMissingOrg_ShouldThrowNotFound() {
        UUID orgId = UUID.randomUUID();
        when(organizationRepository.findByOrgId(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addEmployee(orgId, new AddEmployeeRequest(UUID.randomUUID(), "WORKER")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Организация не найдена");
        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("addEmployee: уже состоит в орг → 409 conflict")
    void addEmployee_GivenAlreadyEmployed_ShouldThrowConflict() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(organizationRepository.findByOrgId(orgId)).thenReturn(Optional.of(
                OrganizationReadModel.builder().orgId(orgId).build()));
        when(employeeRepository.existsByUserIdAndOrgIdAndIsActiveTrue(userId, orgId)).thenReturn(true);

        AppException ex = assertThrowsApp(
                () -> service.addEmployee(orgId, new AddEmployeeRequest(userId, "WORKER")));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("removeEmployee: активный сотрудник → деактивируется, removedAt проставляется")
    void removeEmployee_GivenActive_ShouldDeactivate() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OrganizationEmployee emp = OrganizationEmployee.builder()
                .userId(userId).orgId(orgId).role("WORKER")
                .joinedAt(LocalDateTime.now().minusDays(10)).isActive(true).build();
        when(employeeRepository.findByUserIdAndOrgIdAndIsActiveTrue(userId, orgId))
                .thenReturn(Optional.of(emp));

        service.removeEmployee(orgId, userId);

        assertThat(emp.getIsActive()).isFalse();
        assertThat(emp.getRemovedAt()).isNotNull();
        verify(employeeRepository).save(emp);
    }

    @Test
    @DisplayName("removeEmployee: сотрудник не найден → 404")
    void removeEmployee_GivenMissing_ShouldThrowNotFound() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(employeeRepository.findByUserIdAndOrgIdAndIsActiveTrue(userId, orgId))
                .thenReturn(Optional.empty());

        AppException ex = assertThrowsApp(() -> service.removeEmployee(orgId, userId));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("blockEmployee: активный → блокируется и публикуется employee.status.changed")
    void blockEmployee_GivenActive_ShouldBlockAndPublish() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OrganizationEmployee emp = OrganizationEmployee.builder()
                .userId(userId).orgId(orgId).role("WORKER")
                .joinedAt(LocalDateTime.now()).isActive(true).isBlocked(false).build();
        when(employeeRepository.findByUserIdAndOrgIdAndIsActiveTrue(userId, orgId))
                .thenReturn(Optional.of(emp));

        service.blockEmployee(orgId, userId);

        assertThat(emp.getIsBlocked()).isTrue();
        assertThat(emp.getBlockedAt()).isNotNull();

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.ORGANIZATION_EXCHANGE),
                eq(RabbitMQConfig.EMPLOYEE_STATUS_CHANGED_KEY),
                payloadCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload).containsEntry("blocked", true);
        assertThat(payload).containsEntry("userId", userId.toString());
    }

    @Test
    @DisplayName("blockEmployee: уже заблокирован → 409 conflict, событие не публикуется")
    void blockEmployee_GivenAlreadyBlocked_ShouldThrowConflict() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OrganizationEmployee emp = OrganizationEmployee.builder()
                .userId(userId).orgId(orgId).role("WORKER")
                .isActive(true).isBlocked(true).build();
        when(employeeRepository.findByUserIdAndOrgIdAndIsActiveTrue(userId, orgId))
                .thenReturn(Optional.of(emp));

        AppException ex = assertThrowsApp(() -> service.blockEmployee(orgId, userId));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("unblockEmployee: разблокирует и публикует событие с blocked=false")
    void unblockEmployee_GivenBlocked_ShouldUnblockAndPublish() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OrganizationEmployee emp = OrganizationEmployee.builder()
                .userId(userId).orgId(orgId).role("WORKER")
                .isActive(true).isBlocked(true).blockedAt(LocalDateTime.now()).build();
        when(employeeRepository.findByUserIdAndOrgIdAndIsActiveTrue(userId, orgId))
                .thenReturn(Optional.of(emp));

        service.unblockEmployee(orgId, userId);

        assertThat(emp.getIsBlocked()).isFalse();
        assertThat(emp.getBlockedAt()).isNull();

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.ORGANIZATION_EXCHANGE),
                eq(RabbitMQConfig.EMPLOYEE_STATUS_CHANGED_KEY),
                payloadCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload).containsEntry("blocked", false);
    }

    @Test
    @DisplayName("unblockEmployee: не заблокирован → 409 conflict")
    void unblockEmployee_GivenNotBlocked_ShouldThrowConflict() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OrganizationEmployee emp = OrganizationEmployee.builder()
                .userId(userId).orgId(orgId).role("WORKER")
                .isActive(true).isBlocked(false).build();
        when(employeeRepository.findByUserIdAndOrgIdAndIsActiveTrue(userId, orgId))
                .thenReturn(Optional.of(emp));

        AppException ex = assertThrowsApp(() -> service.unblockEmployee(orgId, userId));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    private static AppException assertThrowsApp(Runnable r) {
        try {
            r.run();
        } catch (AppException e) {
            return e;
        }
        throw new AssertionError("Expected AppException");
    }
}
