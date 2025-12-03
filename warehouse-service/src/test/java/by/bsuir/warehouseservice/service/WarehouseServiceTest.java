package by.bsuir.warehouseservice.service;

import by.bsuir.warehouseservice.dto.request.CreateWarehouseRequest;
import by.bsuir.warehouseservice.dto.request.UpdateWarehouseRequest;
import by.bsuir.warehouseservice.dto.response.WarehouseResponse;
import by.bsuir.warehouseservice.exception.AppException;
import by.bsuir.warehouseservice.model.entity.WarehouseEvent;
import by.bsuir.warehouseservice.model.entity.WarehouseReadModel;
import by.bsuir.warehouseservice.repository.WarehouseEventRepository;
import by.bsuir.warehouseservice.repository.WarehouseReadModelRepository;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WarehouseService Unit Tests")
class WarehouseServiceTest {

    @Mock
    private WarehouseReadModelRepository readModelRepository;

    @Mock
    private WarehouseEventRepository eventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private WarehouseService warehouseService;

    @Test
    @DisplayName("createWarehouse: Given valid request Should create and return warehouse")
    void createWarehouse_GivenValidRequest_ShouldCreateAndReturnWarehouse() {
        UUID orgId = UUID.randomUUID();
        UUID responsibleUserId = UUID.randomUUID();
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                orgId,
                "Центральный склад",
                "г. Минск, ул. Ленина, 1",
                responsibleUserId
        );

        when(readModelRepository.existsByOrgIdAndName(orgId, "Центральный склад")).thenReturn(false);
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(eventRepository.save(any(WarehouseEvent.class))).thenReturn(null);
        when(readModelRepository.save(any(WarehouseReadModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        WarehouseResponse response = warehouseService.createWarehouse(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Центральный склад");
        assertThat(response.orgId()).isEqualTo(orgId);
        assertThat(response.responsibleUserId()).isEqualTo(responsibleUserId);
        assertThat(response.isActive()).isTrue();

        verify(readModelRepository).existsByOrgIdAndName(orgId, "Центральный склад");
        verify(eventRepository).save(any(WarehouseEvent.class));
        verify(readModelRepository).save(any(WarehouseReadModel.class));
    }

    @Test
    @DisplayName("createWarehouse: Given duplicate name Should throw conflict exception")
    void createWarehouse_GivenDuplicateName_ShouldThrowConflictException() {
        UUID orgId = UUID.randomUUID();
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                orgId,
                "Existing Warehouse",
                "Address",
                null
        );

        when(readModelRepository.existsByOrgIdAndName(orgId, "Existing Warehouse")).thenReturn(true);

        assertThatThrownBy(() -> warehouseService.createWarehouse(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("уже существует");

        verify(readModelRepository).existsByOrgIdAndName(orgId, "Existing Warehouse");
        verify(readModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("getWarehouse: Given existing warehouseId Should return warehouse")
    void getWarehouse_GivenExistingWarehouseId_ShouldReturnWarehouse() {
        UUID warehouseId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        WarehouseReadModel warehouse = WarehouseReadModel.builder()
                .warehouseId(warehouseId)
                .orgId(orgId)
                .name("Test Warehouse")
                .address("Test Address")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(readModelRepository.findByWarehouseId(warehouseId)).thenReturn(Optional.of(warehouse));

        WarehouseResponse response = warehouseService.getWarehouse(warehouseId);

        assertThat(response).isNotNull();
        assertThat(response.warehouseId()).isEqualTo(warehouseId);
        assertThat(response.name()).isEqualTo("Test Warehouse");
        assertThat(response.orgId()).isEqualTo(orgId);

        verify(readModelRepository).findByWarehouseId(warehouseId);
    }

    @Test
    @DisplayName("getWarehouse: Given non-existing warehouseId Should throw not found exception")
    void getWarehouse_GivenNonExistingWarehouseId_ShouldThrowNotFoundException() {
        UUID warehouseId = UUID.randomUUID();

        when(readModelRepository.findByWarehouseId(warehouseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> warehouseService.getWarehouse(warehouseId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найден");

        verify(readModelRepository).findByWarehouseId(warehouseId);
    }

    @Test
    @DisplayName("getWarehousesByOrganization: Should return list of warehouses")
    void getWarehousesByOrganization_ShouldReturnListOfWarehouses() {
        UUID orgId = UUID.randomUUID();

        WarehouseReadModel wh1 = WarehouseReadModel.builder()
                .warehouseId(UUID.randomUUID())
                .orgId(orgId)
                .name("Warehouse 1")
                .isActive(true)
                .build();

        WarehouseReadModel wh2 = WarehouseReadModel.builder()
                .warehouseId(UUID.randomUUID())
                .orgId(orgId)
                .name("Warehouse 2")
                .isActive(true)
                .build();

        when(readModelRepository.findByOrgId(orgId)).thenReturn(List.of(wh1, wh2));

        List<WarehouseResponse> responses = warehouseService.getWarehousesByOrganization(orgId);

        assertThat(responses).hasSize(2);
        verify(readModelRepository).findByOrgId(orgId);
    }

    @Test
    @DisplayName("getWarehousesByOrganization: Given no warehouses Should return empty list")
    void getWarehousesByOrganization_GivenNoWarehouses_ShouldReturnEmptyList() {
        UUID orgId = UUID.randomUUID();

        when(readModelRepository.findByOrgId(orgId)).thenReturn(List.of());

        List<WarehouseResponse> responses = warehouseService.getWarehousesByOrganization(orgId);

        assertThat(responses).isEmpty();
        verify(readModelRepository).findByOrgId(orgId);
    }

    @Test
    @DisplayName("getActiveWarehousesByOrganization: Should return only active warehouses")
    void getActiveWarehousesByOrganization_ShouldReturnOnlyActiveWarehouses() {
        UUID orgId = UUID.randomUUID();

        WarehouseReadModel wh1 = WarehouseReadModel.builder()
                .warehouseId(UUID.randomUUID())
                .orgId(orgId)
                .name("Active Warehouse")
                .isActive(true)
                .build();

        when(readModelRepository.findByOrgIdAndIsActiveTrue(orgId)).thenReturn(List.of(wh1));

        List<WarehouseResponse> responses = warehouseService.getActiveWarehousesByOrganization(orgId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).isActive()).isTrue();
        verify(readModelRepository).findByOrgIdAndIsActiveTrue(orgId);
    }

    @Test
    @DisplayName("getAllWarehouses: Should return all warehouses")
    void getAllWarehouses_ShouldReturnAllWarehouses() {
        WarehouseReadModel wh1 = WarehouseReadModel.builder()
                .warehouseId(UUID.randomUUID())
                .orgId(UUID.randomUUID())
                .name("Warehouse 1")
                .isActive(true)
                .build();

        WarehouseReadModel wh2 = WarehouseReadModel.builder()
                .warehouseId(UUID.randomUUID())
                .orgId(UUID.randomUUID())
                .name("Warehouse 2")
                .isActive(false)
                .build();

        when(readModelRepository.findAll()).thenReturn(List.of(wh1, wh2));

        List<WarehouseResponse> responses = warehouseService.getAllWarehouses();

        assertThat(responses).hasSize(2);
        verify(readModelRepository).findAll();
    }

    @Test
    @DisplayName("updateWarehouse: Given valid request Should update and return warehouse")
    void updateWarehouse_GivenValidRequest_ShouldUpdateAndReturnWarehouse() {
        UUID warehouseId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UpdateWarehouseRequest request = new UpdateWarehouseRequest(
                "Updated Name",
                "Updated Address",
                UUID.randomUUID(),
                true
        );

        WarehouseReadModel warehouse = WarehouseReadModel.builder()
                .warehouseId(warehouseId)
                .orgId(orgId)
                .name("Old Name")
                .address("Old Address")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(readModelRepository.findByWarehouseId(warehouseId)).thenReturn(Optional.of(warehouse));
        when(readModelRepository.existsByOrgIdAndName(orgId, "Updated Name")).thenReturn(false);
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(eventRepository.save(any())).thenReturn(null);
        when(readModelRepository.save(any(WarehouseReadModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        WarehouseResponse response = warehouseService.updateWarehouse(warehouseId, request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Updated Name");
        verify(readModelRepository).findByWarehouseId(warehouseId);
        verify(readModelRepository).save(any(WarehouseReadModel.class));
    }

    @Test
    @DisplayName("updateWarehouse: Given duplicate name Should throw conflict exception")
    void updateWarehouse_GivenDuplicateName_ShouldThrowConflictException() {
        UUID warehouseId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UpdateWarehouseRequest request = new UpdateWarehouseRequest(
                "Duplicate Name",
                null,
                null,
                null
        );

        WarehouseReadModel warehouse = WarehouseReadModel.builder()
                .warehouseId(warehouseId)
                .orgId(orgId)
                .name("Old Name")
                .isActive(true)
                .build();

        when(readModelRepository.findByWarehouseId(warehouseId)).thenReturn(Optional.of(warehouse));
        when(readModelRepository.existsByOrgIdAndName(orgId, "Duplicate Name")).thenReturn(true);

        assertThatThrownBy(() -> warehouseService.updateWarehouse(warehouseId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("уже существует");

        verify(readModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("activateWarehouse: Given inactive warehouse Should activate it")
    void activateWarehouse_GivenInactiveWarehouse_ShouldActivateIt() {
        UUID warehouseId = UUID.randomUUID();

        WarehouseReadModel warehouse = WarehouseReadModel.builder()
                .warehouseId(warehouseId)
                .orgId(UUID.randomUUID())
                .name("Test Warehouse")
                .isActive(false)
                .build();

        when(readModelRepository.findByWarehouseId(warehouseId)).thenReturn(Optional.of(warehouse));
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(eventRepository.save(any())).thenReturn(null);
        when(readModelRepository.save(any(WarehouseReadModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        WarehouseResponse response = warehouseService.activateWarehouse(warehouseId);

        assertThat(response).isNotNull();
        assertThat(response.isActive()).isTrue();
        verify(readModelRepository).save(any(WarehouseReadModel.class));
    }

    @Test
    @DisplayName("activateWarehouse: Given already active warehouse Should throw exception")
    void activateWarehouse_GivenAlreadyActiveWarehouse_ShouldThrowException() {
        UUID warehouseId = UUID.randomUUID();

        WarehouseReadModel warehouse = WarehouseReadModel.builder()
                .warehouseId(warehouseId)
                .orgId(UUID.randomUUID())
                .name("Test Warehouse")
                .isActive(true)
                .build();

        when(readModelRepository.findByWarehouseId(warehouseId)).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> warehouseService.activateWarehouse(warehouseId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("уже активен");

        verify(readModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("deactivateWarehouse: Given active warehouse Should deactivate it")
    void deactivateWarehouse_GivenActiveWarehouse_ShouldDeactivateIt() {
        UUID warehouseId = UUID.randomUUID();

        WarehouseReadModel warehouse = WarehouseReadModel.builder()
                .warehouseId(warehouseId)
                .orgId(UUID.randomUUID())
                .name("Test Warehouse")
                .isActive(true)
                .build();

        when(readModelRepository.findByWarehouseId(warehouseId)).thenReturn(Optional.of(warehouse));
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(eventRepository.save(any())).thenReturn(null);
        when(readModelRepository.save(any(WarehouseReadModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        WarehouseResponse response = warehouseService.deactivateWarehouse(warehouseId);

        assertThat(response).isNotNull();
        assertThat(response.isActive()).isFalse();
        verify(readModelRepository).save(any(WarehouseReadModel.class));
    }

    @Test
    @DisplayName("deleteWarehouse: Given existing warehouse Should delete it")
    void deleteWarehouse_GivenExistingWarehouse_ShouldDeleteIt() {
        UUID warehouseId = UUID.randomUUID();

        WarehouseReadModel warehouse = WarehouseReadModel.builder()
                .warehouseId(warehouseId)
                .orgId(UUID.randomUUID())
                .name("Test Warehouse")
                .isActive(true)
                .build();

        when(readModelRepository.findByWarehouseId(warehouseId)).thenReturn(Optional.of(warehouse));
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(eventRepository.save(any())).thenReturn(null);
        doNothing().when(readModelRepository).delete(warehouse);
        doNothing().when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        warehouseService.deleteWarehouse(warehouseId);

        verify(readModelRepository).delete(warehouse);
        verify(eventRepository).save(any());
    }

    @Test
    @DisplayName("deleteWarehousesByOrganization: Should delete all warehouses for organization")
    void deleteWarehousesByOrganization_ShouldDeleteAllWarehousesForOrganization() {
        UUID orgId = UUID.randomUUID();

        WarehouseReadModel wh1 = WarehouseReadModel.builder()
                .warehouseId(UUID.randomUUID())
                .orgId(orgId)
                .name("Warehouse 1")
                .build();

        WarehouseReadModel wh2 = WarehouseReadModel.builder()
                .warehouseId(UUID.randomUUID())
                .orgId(orgId)
                .name("Warehouse 2")
                .build();

        List<WarehouseReadModel> warehouses = List.of(wh1, wh2);

        when(readModelRepository.findByOrgId(orgId)).thenReturn(warehouses);
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(eventRepository.save(any())).thenReturn(null);
        doNothing().when(readModelRepository).deleteAll(any(Iterable.class));
        doNothing().when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        warehouseService.deleteWarehousesByOrganization(orgId);

        verify(readModelRepository).deleteAll(warehouses);
        verify(eventRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("getWarehouseInfo: Given existing warehouse Should return info map")
    void getWarehouseInfo_GivenExistingWarehouse_ShouldReturnInfoMap() {
        UUID warehouseId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        WarehouseReadModel warehouse = WarehouseReadModel.builder()
                .warehouseId(warehouseId)
                .orgId(orgId)
                .name("Test Warehouse")
                .address("Test Address")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(readModelRepository.findByWarehouseId(warehouseId)).thenReturn(Optional.of(warehouse));

        Map<String, Object> info = warehouseService.getWarehouseInfo(warehouseId);

        assertThat(info).isNotNull();
        assertThat(info.get("warehouseId")).isEqualTo(warehouseId.toString());
        assertThat(info.get("name")).isEqualTo("Test Warehouse");
        assertThat(info.get("orgId")).isEqualTo(orgId.toString());
        verify(readModelRepository).findByWarehouseId(warehouseId);
    }

    @Test
    @DisplayName("getWarehousesInfoByOrganization: Should return list of warehouse info maps")
    void getWarehousesInfoByOrganization_ShouldReturnListOfWarehouseInfoMaps() {
        UUID orgId = UUID.randomUUID();

        WarehouseReadModel wh1 = WarehouseReadModel.builder()
                .warehouseId(UUID.randomUUID())
                .orgId(orgId)
                .name("Warehouse 1")
                .isActive(true)
                .build();

        when(readModelRepository.findByOrgIdAndIsActiveTrue(orgId)).thenReturn(List.of(wh1));

        List<Map<String, Object>> infoList = warehouseService.getWarehousesInfoByOrganization(orgId);

        assertThat(infoList).hasSize(1);
        assertThat(infoList.get(0).get("name")).isEqualTo("Warehouse 1");
        verify(readModelRepository).findByOrgIdAndIsActiveTrue(orgId);
    }
}
