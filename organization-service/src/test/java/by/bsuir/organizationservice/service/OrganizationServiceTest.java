package by.bsuir.organizationservice.service;

import by.bsuir.organizationservice.dto.request.CreateOrganizationRequest;
import by.bsuir.organizationservice.dto.request.UpdateOrganizationRequest;
import by.bsuir.organizationservice.dto.response.OrganizationDumpResponse;
import by.bsuir.organizationservice.dto.response.OrganizationResponse;
import by.bsuir.organizationservice.dto.response.InvitationCodeResponse;
import by.bsuir.organizationservice.exception.AppException;
import by.bsuir.organizationservice.model.entity.OrganizationEvent;
import by.bsuir.organizationservice.model.entity.OrganizationReadModel;
import by.bsuir.organizationservice.model.entity.OrganizationInvitationCode;
import by.bsuir.organizationservice.model.enums.OrganizationStatus;
import by.bsuir.organizationservice.repository.OrganizationEventRepository;
import by.bsuir.organizationservice.repository.OrganizationReadModelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationService Unit Tests")
class OrganizationServiceTest {

    @Mock
    private OrganizationReadModelRepository readModelRepository;

    @Mock
    private OrganizationEventRepository eventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private by.bsuir.organizationservice.repository.OrganizationInvitationCodeRepository invitationCodeRepository;

    @Mock
    private by.bsuir.organizationservice.service.WarehouseClientService warehouseClientService;

    @InjectMocks
    private OrganizationService organizationService;

    @Test
    @DisplayName("createOrganization: Given valid request When creating organization Then should save and return response")
    void createOrganization_GivenValidRequest_WhenCreatingOrganization_ThenShouldSaveAndReturnResponse() {
        UUID directorUserId = UUID.randomUUID();
        CreateOrganizationRequest request = new CreateOrganizationRequest(
                "ООО Тестовая организация",
                "Тестовая",
                "123456789", "г. Минск, ул. Ленина, д. 1"
        );

        when(readModelRepository.existsByUnp(anyString())).thenReturn(false);
        when(readModelRepository.existsByName(anyString())).thenReturn(false);
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(eventRepository.save(any(OrganizationEvent.class))).thenReturn(null);
        when(readModelRepository.save(any(OrganizationReadModel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        OrganizationResponse response = organizationService.createOrganization(request, directorUserId);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("ООО Тестовая организация");
        assertThat(response.shortName()).isEqualTo("Тестовая");
        assertThat(response.unp()).isEqualTo("123456789");
        assertThat(response.status()).isEqualTo(OrganizationStatus.ACTIVE);

        verify(readModelRepository).existsByUnp("123456789");
        verify(readModelRepository).existsByName("ООО Тестовая организация");
        verify(eventRepository).save(any(OrganizationEvent.class));
        verify(readModelRepository).save(any(OrganizationReadModel.class));
    }

    @Test
    @DisplayName("createOrganization: Given duplicate UNP When creating Then should throw conflict exception")
    void createOrganization_GivenDuplicateUnp_WhenCreating_ThenShouldThrowConflictException() {
        UUID directorUserId = UUID.randomUUID();
        CreateOrganizationRequest request = new CreateOrganizationRequest(
                "ООО Тестовая",
                "Тестовая",
                "123456789",
                "Адрес"
        );

        when(readModelRepository.existsByUnp("123456789")).thenReturn(true);

        assertThatThrownBy(() -> organizationService.createOrganization(request, directorUserId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("УНП уже существует");

        verify(readModelRepository).existsByUnp("123456789");
        verify(readModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrganization: Given duplicate name When creating Then should throw conflict exception")
    void createOrganization_GivenDuplicateName_WhenCreating_ThenShouldThrowConflictException() {
        UUID directorUserId = UUID.randomUUID();
        CreateOrganizationRequest request = new CreateOrganizationRequest(
                "ООО Существующая",
                "Существующая",
                "123456789",
                "Адрес"
        );

        when(readModelRepository.existsByUnp(anyString())).thenReturn(false);
        when(readModelRepository.existsByName("ООО Существующая")).thenReturn(true);

        assertThatThrownBy(() -> organizationService.createOrganization(request, directorUserId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("наименованием уже существует");

        verify(readModelRepository).existsByName("ООО Существующая");
        verify(readModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("getOrganization: Given existing org ID When getting Then should return organization")
    void getOrganization_GivenExistingOrgId_WhenGetting_ThenShouldReturnOrganization() {
        UUID orgId = UUID.randomUUID();
        OrganizationReadModel org = OrganizationReadModel.builder()
                .orgId(orgId)
                .name("ООО Тест")
                .shortName("Тест")
                .unp("123456789")
                .address("Адрес")
                .status(OrganizationStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.of(org));

        OrganizationResponse response = organizationService.getOrganization(orgId);

        assertThat(response).isNotNull();
        assertThat(response.orgId()).isEqualTo(orgId);
        assertThat(response.name()).isEqualTo("ООО Тест");
        assertThat(response.unp()).isEqualTo("123456789");

        verify(readModelRepository).findByOrgId(orgId);
    }

    @Test
    @DisplayName("getOrganization: Given non-existing org ID When getting Then should throw not found exception")
    void getOrganization_GivenNonExistingOrgId_WhenGetting_ThenShouldThrowNotFoundException() {
        UUID orgId = UUID.randomUUID();
        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.getOrganization(orgId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найдена");

        verify(readModelRepository).findByOrgId(orgId);
    }

    @Test
    @DisplayName("getAllOrganizations: Should return all organizations")
    void getAllOrganizations_ShouldReturnAllOrganizations() {
        OrganizationReadModel org1 = OrganizationReadModel.builder()
                .orgId(UUID.randomUUID())
                .name("ООО Тест 1")
                .status(OrganizationStatus.ACTIVE)
                .build();

        OrganizationReadModel org2 = OrganizationReadModel.builder()
                .orgId(UUID.randomUUID())
                .name("ООО Тест 2")
                .status(OrganizationStatus.BLOCKED)
                .build();

        when(readModelRepository.findAll()).thenReturn(List.of(org1, org2));

        List<OrganizationResponse> responses = organizationService.getAllOrganizations();

        assertThat(responses).hasSize(2);
        verify(readModelRepository).findAll();
    }

    @Test
    @DisplayName("getOrganizationsByStatus: Given status Should return filtered organizations")
    void getOrganizationsByStatus_GivenStatus_ShouldReturnFilteredOrganizations() {
        OrganizationReadModel org1 = OrganizationReadModel.builder()
                .orgId(UUID.randomUUID())
                .name("ООО Тест 1")
                .status(OrganizationStatus.ACTIVE)
                .build();

        when(readModelRepository.findAllByStatus(OrganizationStatus.ACTIVE)).thenReturn(List.of(org1));

        List<OrganizationResponse> responses = organizationService.getOrganizationsByStatus(OrganizationStatus.ACTIVE);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().status()).isEqualTo(OrganizationStatus.ACTIVE);
        verify(readModelRepository).findAllByStatus(OrganizationStatus.ACTIVE);
    }

    @Test
    @DisplayName("updateOrganization: Given valid request Should update and return response")
    void updateOrganization_GivenValidRequest_ShouldUpdateAndReturnResponse() {
        UUID orgId = UUID.randomUUID();
        UpdateOrganizationRequest request = new UpdateOrganizationRequest(
                "Новое название",
                "Новое",
                "987654321",
                "Новый адрес"
        );

        OrganizationReadModel existingOrg = OrganizationReadModel.builder()
                .orgId(orgId)
                .name("Старое название")
                .shortName("Старое")
                .unp("123456789")
                .address("Старый адрес")
                .status(OrganizationStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.of(existingOrg));
        when(readModelRepository.existsByUnp("987654321")).thenReturn(false);
        when(readModelRepository.existsByName("Новое название")).thenReturn(false);
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(eventRepository.save(any(OrganizationEvent.class))).thenReturn(null);
        when(readModelRepository.save(any(OrganizationReadModel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        OrganizationResponse response = organizationService.updateOrganization(orgId, request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Новое название");
        assertThat(response.unp()).isEqualTo("987654321");

        verify(readModelRepository).findByOrgId(orgId);
        verify(readModelRepository).save(any(OrganizationReadModel.class));
        verify(eventRepository).save(any(OrganizationEvent.class));
    }

    @Test
    @DisplayName("updateOrganization: Given duplicate UNP Should throw conflict exception")
    void updateOrganization_GivenDuplicateUnp_ShouldThrowConflictException() {
        UUID orgId = UUID.randomUUID();
        UpdateOrganizationRequest request = new UpdateOrganizationRequest(
                null,
                null,
                "987654321",
                null
        );

        OrganizationReadModel existingOrg = OrganizationReadModel.builder()
                .orgId(orgId)
                .name("Название")
                .unp("123456789")
                .status(OrganizationStatus.ACTIVE)
                .build();

        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.of(existingOrg));
        when(readModelRepository.existsByUnp("987654321")).thenReturn(true);

        assertThatThrownBy(() -> organizationService.updateOrganization(orgId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("УНП уже существует");

        verify(readModelRepository).findByOrgId(orgId);
        verify(readModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateOrganization: Given non-existing org Should throw not found exception")
    void updateOrganization_GivenNonExistingOrg_ShouldThrowNotFoundException() {
        UUID orgId = UUID.randomUUID();
        UpdateOrganizationRequest request = new UpdateOrganizationRequest("Название", null, null, null);

        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.updateOrganization(orgId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найдена");

        verify(readModelRepository).findByOrgId(orgId);
        verify(readModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteOrganization: Given existing org Should delete and return dump")
    void deleteOrganization_GivenExistingOrg_ShouldDeleteAndReturnDump() {
        UUID orgId = UUID.randomUUID();
        UUID deletedByUserId = UUID.randomUUID();

        OrganizationReadModel org = OrganizationReadModel.builder()
                .orgId(orgId)
                .name("ООО Тест")
                .shortName("Тест")
                .unp("123456789")
                .address("Адрес")
                .status(OrganizationStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.of(org));
        when(eventRepository.findByOrgIdOrderByCreatedAtAsc(orgId)).thenReturn(List.of());
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(eventRepository.save(any(OrganizationEvent.class))).thenReturn(null);
        doNothing().when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        doNothing().when(invitationCodeRepository).deactivateAllByOrgId(orgId);
        doNothing().when(readModelRepository).delete(org);

        OrganizationDumpResponse response = organizationService.deleteOrganization(orgId, deletedByUserId);

        assertThat(response).isNotNull();
        assertThat(response.message()).contains("успешно удалена");
        assertThat(response.organizationData()).isNotNull();

        verify(readModelRepository).findByOrgId(orgId);
        verify(readModelRepository).delete(org);
        verify(invitationCodeRepository).deactivateAllByOrgId(orgId);
        verify(eventRepository).save(any(OrganizationEvent.class));
    }

    @Test
    @DisplayName("deleteOrganization: Given non-existing org Should throw not found exception")
    void deleteOrganization_GivenNonExistingOrg_ShouldThrowNotFoundException() {
        UUID orgId = UUID.randomUUID();
        UUID deletedByUserId = UUID.randomUUID();

        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.deleteOrganization(orgId, deletedByUserId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найдена");

        verify(readModelRepository).findByOrgId(orgId);
        verify(readModelRepository, never()).delete(any());
    }

    @Test
    @DisplayName("generateInvitationCodes: Given active organization Should generate codes")
    void generateInvitationCodes_GivenActiveOrganization_ShouldGenerateCodes() {
        UUID orgId = UUID.randomUUID();

        OrganizationReadModel org = OrganizationReadModel.builder()
                .orgId(orgId)
                .name("ООО Тест")
                .status(OrganizationStatus.ACTIVE)
                .build();

        List<Map<String, Object>> warehouses = List.of(
                Map.of("warehouseId", UUID.randomUUID().toString(), "name", "Warehouse 1")
        );

        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.of(org));
        when(warehouseClientService.getWarehousesByOrganization(orgId)).thenReturn(warehouses);
        doNothing().when(invitationCodeRepository).deactivateAllByOrgId(orgId);
        when(invitationCodeRepository.save(any(OrganizationInvitationCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<InvitationCodeResponse> codes = organizationService.generateInvitationCodes(orgId);

        assertThat(codes).hasSize(1);
        verify(readModelRepository).findByOrgId(orgId);
        verify(invitationCodeRepository).deactivateAllByOrgId(orgId);
        verify(invitationCodeRepository).save(any(OrganizationInvitationCode.class));
    }

    @Test
    @DisplayName("generateInvitationCodes: Given inactive organization Should throw exception")
    void generateInvitationCodes_GivenInactiveOrganization_ShouldThrowException() {
        UUID orgId = UUID.randomUUID();

        OrganizationReadModel org = OrganizationReadModel.builder()
                .orgId(orgId)
                .name("ООО Тест")
                .status(OrganizationStatus.BLOCKED)
                .build();

        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.of(org));

        assertThatThrownBy(() -> organizationService.generateInvitationCodes(orgId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("неактивной организации");

        verify(readModelRepository).findByOrgId(orgId);
        verify(invitationCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("generateInvitationCodes: Given non-existing org Should throw not found exception")
    void generateInvitationCodes_GivenNonExistingOrg_ShouldThrowNotFoundException() {
        UUID orgId = UUID.randomUUID();

        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.generateInvitationCodes(orgId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найдена");

        verify(readModelRepository).findByOrgId(orgId);
    }

    @Test
    @DisplayName("updateOrganization: Given duplicate name Should throw conflict exception")
    void updateOrganization_GivenDuplicateName_ShouldThrowConflictException() {
        UUID orgId = UUID.randomUUID();
        UpdateOrganizationRequest request = new UpdateOrganizationRequest(
                "Existing Name",
                null,
                null,
                null
        );

        OrganizationReadModel existingOrg = OrganizationReadModel.builder()
                .orgId(orgId)
                .name("Old Name")
                .unp("123456789")
                .status(OrganizationStatus.ACTIVE)
                .build();

        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.of(existingOrg));
        when(readModelRepository.existsByName("Existing Name")).thenReturn(true);

        assertThatThrownBy(() -> organizationService.updateOrganization(orgId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("наименованием уже существует");

        verify(readModelRepository).findByOrgId(orgId);
        verify(readModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("regenerateInvitationCodeForWarehouse: Given valid orgId and warehouseId Should regenerate code")
    void regenerateInvitationCodeForWarehouse_GivenValidOrgIdAndWarehouseId_ShouldRegenerateCode() {
        UUID orgId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        OrganizationReadModel org = OrganizationReadModel.builder()
                .orgId(orgId)
                .name("ООО Тест")
                .status(OrganizationStatus.ACTIVE)
                .build();

        Map<String, Object> warehouseInfo = Map.of(
                "warehouseId", warehouseId.toString(),
                "name", "Main Warehouse"
        );

        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.of(org));
        doNothing().when(invitationCodeRepository).deactivateAllByOrgIdAndWarehouseId(orgId, warehouseId);
        when(warehouseClientService.getWarehouseInfo(warehouseId)).thenReturn(warehouseInfo);
        when(invitationCodeRepository.save(any(OrganizationInvitationCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InvitationCodeResponse response = organizationService.regenerateInvitationCodeForWarehouse(orgId, warehouseId);

        assertThat(response).isNotNull();
        assertThat(response.warehouseId()).isEqualTo(warehouseId);
        assertThat(response.warehouseName()).isEqualTo("Main Warehouse");
        verify(readModelRepository).findByOrgId(orgId);
        verify(invitationCodeRepository).deactivateAllByOrgIdAndWarehouseId(orgId, warehouseId);
        verify(invitationCodeRepository).save(any(OrganizationInvitationCode.class));
    }

    @Test
    @DisplayName("regenerateInvitationCodeForWarehouse: Given non-existing org Should throw exception")
    void regenerateInvitationCodeForWarehouse_GivenNonExistingOrg_ShouldThrowException() {
        UUID orgId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.regenerateInvitationCodeForWarehouse(orgId, warehouseId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найдена");

        verify(readModelRepository).findByOrgId(orgId);
    }

    @Test
    @DisplayName("getActiveInvitationCodes: Should return list of active codes")
    void getActiveInvitationCodes_ShouldReturnListOfActiveCodes() {
        UUID orgId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        OrganizationInvitationCode code1 = OrganizationInvitationCode.builder()
                .codeId(UUID.randomUUID())
                .orgId(orgId)
                .warehouseId(warehouseId)
                .invitationCode("CODE123")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .isActive(true)
                .build();

        when(invitationCodeRepository.findByOrgIdAndIsActiveTrue(orgId)).thenReturn(List.of(code1));
        when(warehouseClientService.getWarehouseInfo(warehouseId))
                .thenReturn(Map.of("name", "Test Warehouse"));

        List<InvitationCodeResponse> codes = organizationService.getActiveInvitationCodes(orgId);

        assertThat(codes).hasSize(1);
        assertThat(codes.get(0).invitationCode()).isEqualTo("CODE123");
        verify(invitationCodeRepository).findByOrgIdAndIsActiveTrue(orgId);
    }

    @Test
    @DisplayName("getActiveInvitationCodes: Given no active codes Should return empty list")
    void getActiveInvitationCodes_GivenNoActiveCodes_ShouldReturnEmptyList() {
        UUID orgId = UUID.randomUUID();

        when(invitationCodeRepository.findByOrgIdAndIsActiveTrue(orgId)).thenReturn(List.of());

        List<InvitationCodeResponse> codes = organizationService.getActiveInvitationCodes(orgId);

        assertThat(codes).isEmpty();
        verify(invitationCodeRepository).findByOrgIdAndIsActiveTrue(orgId);
    }

    @Test
    @DisplayName("validateInvitationCode: Given valid code Should return organization and warehouse info")
    void validateInvitationCode_GivenValidCode_ShouldReturnOrganizationAndWarehouseInfo() {
        UUID orgId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        String code = "VALIDCODE123";

        OrganizationInvitationCode invitationCode = OrganizationInvitationCode.builder()
                .codeId(UUID.randomUUID())
                .orgId(orgId)
                .warehouseId(warehouseId)
                .invitationCode(code)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .isActive(true)
                .build();

        OrganizationReadModel org = OrganizationReadModel.builder()
                .orgId(orgId)
                .name("ООО Тест")
                .status(OrganizationStatus.ACTIVE)
                .build();

        when(invitationCodeRepository.findByInvitationCode(code)).thenReturn(Optional.of(invitationCode));
        when(readModelRepository.findByOrgId(orgId)).thenReturn(Optional.of(org));

        Map<String, Object> result = organizationService.validateInvitationCode(code);

        assertThat(result).isNotNull();
        assertThat(result.get("orgId")).isEqualTo(orgId);
        assertThat(result.get("orgName")).isEqualTo("ООО Тест");
        assertThat(result.get("warehouseId")).isEqualTo(warehouseId);
        verify(invitationCodeRepository).findByInvitationCode(code);
        verify(readModelRepository).findByOrgId(orgId);
    }

    @Test
    @DisplayName("validateInvitationCode: Given non-existing code Should throw exception")
    void validateInvitationCode_GivenNonExistingCode_ShouldThrowException() {
        String code = "INVALIDCODE";

        when(invitationCodeRepository.findByInvitationCode(code)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.validateInvitationCode(code))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найден");

        verify(invitationCodeRepository).findByInvitationCode(code);
    }

    @Test
    @DisplayName("validateInvitationCode: Given expired code Should throw exception")
    void validateInvitationCode_GivenExpiredCode_ShouldThrowException() {
        UUID orgId = UUID.randomUUID();
        String code = "EXPIREDCODE";

        OrganizationInvitationCode invitationCode = OrganizationInvitationCode.builder()
                .codeId(UUID.randomUUID())
                .orgId(orgId)
                .invitationCode(code)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .isActive(true)
                .build();

        when(invitationCodeRepository.findByInvitationCode(code)).thenReturn(Optional.of(invitationCode));

        assertThatThrownBy(() -> organizationService.validateInvitationCode(code))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("недействителен или истёк");

        verify(invitationCodeRepository).findByInvitationCode(code);
    }

    @Test
    @DisplayName("validateInvitationCode: Given inactive code Should throw exception")
    void validateInvitationCode_GivenInactiveCode_ShouldThrowException() {
        UUID orgId = UUID.randomUUID();
        String code = "INACTIVECODE";

        OrganizationInvitationCode invitationCode = OrganizationInvitationCode.builder()
                .codeId(UUID.randomUUID())
                .orgId(orgId)
                .invitationCode(code)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .isActive(false)
                .build();

        when(invitationCodeRepository.findByInvitationCode(code)).thenReturn(Optional.of(invitationCode));

        assertThatThrownBy(() -> organizationService.validateInvitationCode(code))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("недействителен или истёк");

        verify(invitationCodeRepository).findByInvitationCode(code);
    }
}
