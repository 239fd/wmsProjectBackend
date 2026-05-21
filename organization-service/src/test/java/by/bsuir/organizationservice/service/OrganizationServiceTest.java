package by.bsuir.organizationservice.service;

import by.bsuir.organizationservice.config.RabbitMQConfig;
import by.bsuir.organizationservice.dto.request.CreateOrganizationRequest;
import by.bsuir.organizationservice.dto.request.UpdateOrganizationRequest;
import by.bsuir.organizationservice.dto.response.OrganizationDumpResponse;
import by.bsuir.organizationservice.dto.response.OrganizationResponse;
import by.bsuir.organizationservice.exception.AppException;
import by.bsuir.organizationservice.model.entity.OrganizationEmployee;
import by.bsuir.organizationservice.model.entity.OrganizationEvent;
import by.bsuir.organizationservice.model.entity.OrganizationReadModel;
import by.bsuir.organizationservice.model.enums.OrganizationStatus;
import by.bsuir.organizationservice.repository.OrganizationEmployeeRepository;
import by.bsuir.organizationservice.repository.OrganizationEventRepository;
import by.bsuir.organizationservice.repository.OrganizationInvitationCodeRepository;
import by.bsuir.organizationservice.repository.OrganizationReadModelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationService — модульные тесты")
class OrganizationServiceTest {

    @Mock private OrganizationReadModelRepository readModelRepository;
    @Mock private OrganizationEventRepository eventRepository;
    @Mock private OrganizationInvitationCodeRepository invitationCodeRepository;
    @Mock private OrganizationEmployeeRepository employeeRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private WarehouseClientService warehouseClientService;
    @Mock private RestTemplate restTemplate;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private OrganizationService organizationService;

    private UUID directorUserId;

    @BeforeEach
    void setUp() {
        directorUserId = UUID.randomUUID();
        ReflectionTestUtils.setField(organizationService, "invitationCodeTtlHours", 24);
    }

    @Test
    @DisplayName("createOrganization: валидный запрос → создаёт орг, employee директора, публикует событие")
    void createOrganization_GivenValid_ShouldCreateAndPublish() {
        CreateOrganizationRequest req = new CreateOrganizationRequest(
                "ООО Ромашка", "Ромашка", "123456789", "Минск, ул. Ленина 1");
        when(readModelRepository.existsByUnp("123456789")).thenReturn(false);
        when(readModelRepository.existsByName("ООО Ромашка")).thenReturn(false);

        OrganizationResponse response = organizationService.createOrganization(req, directorUserId);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("ООО Ромашка");
        assertThat(response.unp()).isEqualTo("123456789");
        assertThat(response.status()).isEqualTo(OrganizationStatus.ACTIVE);

        ArgumentCaptor<OrganizationReadModel> rmCaptor =
                ArgumentCaptor.forClass(OrganizationReadModel.class);
        verify(readModelRepository).save(rmCaptor.capture());
        assertThat(rmCaptor.getValue().getStatus()).isEqualTo(OrganizationStatus.ACTIVE);

        ArgumentCaptor<OrganizationEmployee> empCaptor =
                ArgumentCaptor.forClass(OrganizationEmployee.class);
        verify(employeeRepository).save(empCaptor.capture());
        assertThat(empCaptor.getValue().getUserId()).isEqualTo(directorUserId);
        assertThat(empCaptor.getValue().getRole()).isEqualTo("DIRECTOR");
        assertThat(empCaptor.getValue().getIsActive()).isTrue();

        verify(eventRepository).save(any(OrganizationEvent.class));
        verify(restTemplate).patchForObject(anyString(), any(), eq(java.util.Map.class));
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.ORGANIZATION_EXCHANGE),
                eq(RabbitMQConfig.ORGANIZATION_CREATED_KEY),
                any(Object.class));
    }

    @Test
    @DisplayName("createOrganization: дубликат УНП → 409 conflict с русским сообщением")
    void createOrganization_GivenDuplicateUnp_ShouldThrowConflict() {
        CreateOrganizationRequest req = new CreateOrganizationRequest(
                "ООО Ромашка", null, "123456789", "адрес");
        when(readModelRepository.existsByUnp("123456789")).thenReturn(true);

        assertThatThrownBy(() -> organizationService.createOrganization(req, directorUserId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("ИНН");

        AppException ex = catchAppException(
                () -> organizationService.createOrganization(req, directorUserId));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(readModelRepository, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("createOrganization: дубликат наименования → 409 conflict")
    void createOrganization_GivenDuplicateName_ShouldThrowConflict() {
        CreateOrganizationRequest req = new CreateOrganizationRequest(
                "ООО Ромашка", null, "123456789", "адрес");
        when(readModelRepository.existsByUnp("123456789")).thenReturn(false);
        when(readModelRepository.existsByName("ООО Ромашка")).thenReturn(true);

        assertThatThrownBy(() -> organizationService.createOrganization(req, directorUserId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("наименование");
        verify(readModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrganization: SSO PATCH упал → пробрасывает RuntimeException, save не доходит до публикации")
    void createOrganization_WhenSsoUpdateFails_ShouldThrowRuntime() {
        CreateOrganizationRequest req = new CreateOrganizationRequest(
                "ООО Ромашка", null, "123456789", "адрес");
        when(readModelRepository.existsByUnp("123456789")).thenReturn(false);
        when(readModelRepository.existsByName("ООО Ромашка")).thenReturn(false);
        when(restTemplate.patchForObject(anyString(), any(), eq(java.util.Map.class)))
                .thenThrow(new RuntimeException("SSO down"));

        assertThatThrownBy(() -> organizationService.createOrganization(req, directorUserId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("привязку");
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("getOrganization: существует → возвращает response")
    void getOrganization_GivenExisting_ShouldReturn() {
        UUID orgId = UUID.randomUUID();
        OrganizationReadModel rm = sampleOrg(orgId);
        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.of(rm));

        OrganizationResponse response = organizationService.getOrganization(orgId);

        assertThat(response.orgId()).isEqualTo(orgId);
        assertThat(response.name()).isEqualTo(rm.getName());
    }

    @Test
    @DisplayName("getOrganization: не найдена → 404 not found")
    void getOrganization_GivenMissing_ShouldThrowNotFound() {
        UUID orgId = UUID.randomUUID();
        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.empty());

        AppException ex = catchAppException(() -> organizationService.getOrganization(orgId));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).contains("не найдена");
    }

    @Test
    @DisplayName("updateOrganization: меняем УНП на свободный → успешно сохраняет и публикует событие updated")
    void updateOrganization_GivenChangedUnpFree_ShouldSucceed() {
        UUID orgId = UUID.randomUUID();
        OrganizationReadModel rm = sampleOrg(orgId);
        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.of(rm));
        when(readModelRepository.existsByUnp("999999999")).thenReturn(false);

        UpdateOrganizationRequest req = new UpdateOrganizationRequest(
                null, null, "999999999", null);
        OrganizationResponse response = organizationService.updateOrganization(orgId, req);

        assertThat(response.unp()).isEqualTo("999999999");
        verify(eventRepository).save(any(OrganizationEvent.class));
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.ORGANIZATION_EXCHANGE),
                eq(RabbitMQConfig.ORGANIZATION_UPDATED_KEY),
                any(Object.class));
    }

    @Test
    @DisplayName("updateOrganization: меняем УНП на занятый → 409 conflict")
    void updateOrganization_GivenChangedUnpTaken_ShouldThrowConflict() {
        UUID orgId = UUID.randomUUID();
        OrganizationReadModel rm = sampleOrg(orgId);
        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.of(rm));
        when(readModelRepository.existsByUnp("999999999")).thenReturn(true);

        UpdateOrganizationRequest req = new UpdateOrganizationRequest(
                null, null, "999999999", null);
        AppException ex = catchAppException(
                () -> organizationService.updateOrganization(orgId, req));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(readModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateOrganization: УНП не меняется → uniqueness check не вызывается")
    void updateOrganization_GivenSameUnp_ShouldSkipUniqueCheck() {
        UUID orgId = UUID.randomUUID();
        OrganizationReadModel rm = sampleOrg(orgId);
        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.of(rm));

        UpdateOrganizationRequest req = new UpdateOrganizationRequest(
                null, null, rm.getUnp(), null);
        organizationService.updateOrganization(orgId, req);

        verify(readModelRepository, never()).existsByUnp(anyString());
    }

    @Test
    @DisplayName("deleteOrganization: активная орг → переводится в ARCHIVED, сотрудники кроме DIRECTOR увольняются")
    void deleteOrganization_GivenActive_ShouldArchiveAndFireNonDirectors() {
        UUID orgId = UUID.randomUUID();
        OrganizationReadModel rm = sampleOrg(orgId);
        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.of(rm));
        when(eventRepository.findByOrgIdOrderByCreatedAtAsc(orgId)).thenReturn(List.of());
        when(eventRepository.findMaxVersionByOrgId(orgId)).thenReturn(1);

        OrganizationEmployee director = OrganizationEmployee.builder()
                .userId(directorUserId).orgId(orgId).role("DIRECTOR")
                .joinedAt(LocalDateTime.now().minusDays(30)).isActive(true).build();
        OrganizationEmployee worker = OrganizationEmployee.builder()
                .userId(UUID.randomUUID()).orgId(orgId).role("WORKER")
                .joinedAt(LocalDateTime.now().minusDays(5)).isActive(true).build();
        OrganizationEmployee accountant = OrganizationEmployee.builder()
                .userId(UUID.randomUUID()).orgId(orgId).role("ACCOUNTANT")
                .joinedAt(LocalDateTime.now().minusDays(15)).isActive(true).build();
        when(employeeRepository.findByOrgIdAndIsActiveTrue(orgId))
                .thenReturn(new ArrayList<>(List.of(director, worker, accountant)));

        OrganizationDumpResponse dump = organizationService.deleteOrganization(orgId, directorUserId);

        assertThat(dump).isNotNull();
        assertThat(dump.organizationData()).containsKey("orgId");
        assertThat(rm.getStatus()).isEqualTo(OrganizationStatus.ARCHIVED);

        assertThat(director.getIsActive()).isTrue();
        assertThat(worker.getIsActive()).isFalse();
        assertThat(accountant.getIsActive()).isFalse();
        assertThat(worker.getRemovedAt()).isNotNull();
        assertThat(accountant.getRemovedAt()).isNotNull();

        verify(employeeRepository, times(2)).save(any(OrganizationEmployee.class));
        verify(invitationCodeRepository).deactivateAllByOrgId(orgId);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.ORGANIZATION_EXCHANGE),
                eq(RabbitMQConfig.ORGANIZATION_ARCHIVED_KEY),
                any(Object.class));
    }

    @Test
    @DisplayName("deleteOrganization: уже архивирована → 409 conflict")
    void deleteOrganization_GivenAlreadyArchived_ShouldThrowConflict() {
        UUID orgId = UUID.randomUUID();
        OrganizationReadModel rm = sampleOrg(orgId);
        rm.setStatus(OrganizationStatus.ARCHIVED);
        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.of(rm));

        AppException ex = catchAppException(
                () -> organizationService.deleteOrganization(orgId, directorUserId));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(employeeRepository, never()).findByOrgIdAndIsActiveTrue(any());
    }

    @Test
    @DisplayName("deleteOrganization: орг не найдена → 404")
    void deleteOrganization_GivenMissing_ShouldThrowNotFound() {
        UUID orgId = UUID.randomUUID();
        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.empty());

        AppException ex = catchAppException(
                () -> organizationService.deleteOrganization(orgId, directorUserId));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getAllOrganizations: маппит репозиторий в список response")
    void getAllOrganizations_ShouldMapAll() {
        OrganizationReadModel rm1 = sampleOrg(UUID.randomUUID());
        OrganizationReadModel rm2 = sampleOrg(UUID.randomUUID());
        when(readModelRepository.findAll()).thenReturn(List.of(rm1, rm2));

        List<OrganizationResponse> all = organizationService.getAllOrganizations();

        assertThat(all).hasSize(2);
    }

    @Test
    @DisplayName("getOrganizationsByStatus: фильтр по статусу делегирует в репозиторий")
    void getOrganizationsByStatus_ShouldDelegate() {
        when(readModelRepository.findAllByStatus(OrganizationStatus.ARCHIVED))
                .thenReturn(List.of(sampleOrg(UUID.randomUUID())));

        List<OrganizationResponse> archived =
                organizationService.getOrganizationsByStatus(OrganizationStatus.ARCHIVED);

        assertThat(archived).hasSize(1);
        verify(readModelRepository).findAllByStatus(OrganizationStatus.ARCHIVED);
    }

    @Test
    @DisplayName("createOrganization: RabbitMQ упал → орг всё равно создаётся (graceful)")
    void createOrganization_WhenRabbitFails_ShouldStillSucceed() {
        CreateOrganizationRequest req = new CreateOrganizationRequest(
                "ООО Ромашка", null, "123456789", "адрес");
        when(readModelRepository.existsByUnp("123456789")).thenReturn(false);
        when(readModelRepository.existsByName("ООО Ромашка")).thenReturn(false);
        lenient().doThrow(new RuntimeException("rabbit down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        OrganizationResponse response = organizationService.createOrganization(req, directorUserId);

        assertThat(response).isNotNull();
        assertThat(response.unp()).isEqualTo("123456789");
        verify(readModelRepository).save(any());
    }

    private OrganizationReadModel sampleOrg(UUID orgId) {
        return OrganizationReadModel.builder()
                .orgId(orgId)
                .name("ООО Ромашка")
                .shortName("Ромашка")
                .unp("123456789")
                .address("Минск, ул. Ленина 1")
                .status(OrganizationStatus.ACTIVE)
                .createdAt(LocalDateTime.now().minusDays(10))
                .updatedAt(LocalDateTime.now().minusDays(10))
                .build();
    }

    private static AppException catchAppException(Runnable r) {
        try {
            r.run();
        } catch (AppException e) {
            return e;
        }
        throw new AssertionError("Expected AppException but none was thrown");
    }
}
