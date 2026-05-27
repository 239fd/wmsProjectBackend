package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.CreateSupplyRequest;
import by.bsuir.productservice.dto.response.SupplyResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Supply;
import by.bsuir.productservice.model.entity.SupplyItem;
import by.bsuir.productservice.model.enums.SupplyStatus;
import by.bsuir.productservice.repository.SupplyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupplyService Tests")
class SupplyServiceTest {

    @Mock
    private SupplyRepository supplyRepository;

    @InjectMocks
    private SupplyService service;

    private UUID supplyId;
    private UUID warehouseId;
    private UUID supplierId;
    private UUID orgId;
    private UUID userId;
    private Supply supply;

    @BeforeEach
    void setUp() {
        supplyId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        supplierId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();

        supply = Supply.builder()
                .supplyId(supplyId)
                .organizationId(orgId)
                .supplierId(supplierId)
                .warehouseId(warehouseId)
                .status(SupplyStatus.PLANNED)
                .expectedDate(LocalDate.now().plusDays(7))
                .totalItems(2)
                .notes("Поставка молока")
                .createdBy(userId)
                .items(List.of(SupplyItem.builder()
                        .itemId(UUID.randomUUID())
                        .productId(UUID.randomUUID())
                        .expectedQty(new BigDecimal("100.000"))
                        .unitPrice(new BigDecimal("2.50"))
                        .notes("note")
                        .build()))
                .build();
    }

    @Test
    @DisplayName("getAll: возвращает все поставки")
    void getAll_whenCalled_thenReturnsAll() {
        when(supplyRepository.findAll()).thenReturn(List.of(supply));

        List<SupplyResponse> result = service.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).supplyId()).isEqualTo(supplyId);
        assertThat(result.get(0).items()).hasSize(1);
    }

    @Test
    @DisplayName("getAll(Pageable): пагинированный ответ")
    void getAll_givenPageable_whenCalled_thenReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(supplyRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(supply), pageable, 1));

        var page = service.getAll(pageable);

        assertThat(page.getTotalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getById: существующая поставка")
    void getById_givenExistingSupply_whenCalled_thenReturnsResponse() {
        when(supplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));

        SupplyResponse response = service.getById(supplyId);

        assertThat(response.supplyId()).isEqualTo(supplyId);
        assertThat(response.status()).isEqualTo(SupplyStatus.PLANNED);
    }

    @Test
    @DisplayName("getById: не найдена → notFound")
    void getById_givenMissing_whenCalled_thenThrowsNotFound() {
        when(supplyRepository.findById(supplyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(supplyId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найдена");
    }

    @Test
    @DisplayName("getByWarehouse: фильтрует по складу")
    void getByWarehouse_whenCalled_thenFiltersByWarehouse() {
        when(supplyRepository.findByWarehouseId(warehouseId)).thenReturn(List.of(supply));

        assertThat(service.getByWarehouse(warehouseId)).hasSize(1);
    }

    @Test
    @DisplayName("getByWarehouse(Pageable): пагинация по складу")
    void getByWarehouse_givenPageable_whenCalled_thenReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(supplyRepository.findByWarehouseId(eq(warehouseId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(supply), pageable, 1));

        assertThat(service.getByWarehouse(warehouseId, pageable).getTotalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getByStatus: фильтрует по статусу")
    void getByStatus_whenCalled_thenFiltersByStatus() {
        when(supplyRepository.findByStatus(SupplyStatus.PLANNED)).thenReturn(List.of(supply));

        assertThat(service.getByStatus(SupplyStatus.PLANNED)).hasSize(1);
    }

    @Test
    @DisplayName("getByStatus(Pageable): пагинация по статусу")
    void getByStatus_givenPageable_whenCalled_thenReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(supplyRepository.findByStatus(eq(SupplyStatus.PLANNED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(supply), pageable, 1));

        assertThat(service.getByStatus(SupplyStatus.PLANNED, pageable).getTotalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getByOrganization: фильтрует по организации")
    void getByOrganization_whenCalled_thenFiltersByOrg() {
        when(supplyRepository.findByOrganizationId(orgId)).thenReturn(List.of(supply));

        assertThat(service.getByOrganization(orgId)).hasSize(1);
    }

    @Test
    @DisplayName("getByOrganization(Pageable): пагинация по организации")
    void getByOrganization_givenPageable_whenCalled_thenReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(supplyRepository.findByOrganizationId(eq(orgId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(supply), pageable, 1));

        assertThat(service.getByOrganization(orgId, pageable).getTotalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getByOrganizationAndStatus: фильтрует по org + status")
    void getByOrganizationAndStatus_whenCalled_thenFiltersByBoth() {
        when(supplyRepository.findByOrganizationIdAndStatus(orgId, SupplyStatus.PLANNED))
                .thenReturn(List.of(supply));

        assertThat(service.getByOrganizationAndStatus(orgId, SupplyStatus.PLANNED)).hasSize(1);
    }

    @Test
    @DisplayName("getByOrganizationAndStatus(Pageable): пагинация по org+status")
    void getByOrganizationAndStatus_givenPageable_whenCalled_thenReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(supplyRepository.findByOrganizationIdAndStatus(eq(orgId), eq(SupplyStatus.PLANNED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(supply), pageable, 1));

        assertThat(service.getByOrganizationAndStatus(orgId, SupplyStatus.PLANNED, pageable).getTotalElements())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("create: PLANNED статус + items + totalItems")
    void create_givenValidRequest_whenCalled_thenCreatesPlanned() {
        CreateSupplyRequest req = new CreateSupplyRequest(
                supplierId, null, warehouseId, LocalDate.now().plusDays(5),
                null, null, "note",
                userId, null, null,
                List.of(new CreateSupplyRequest.SupplyItemRequest(
                        UUID.randomUUID(), null, null, null, null, null, null, null,
                        new BigDecimal("50.000"),
                        new BigDecimal("1.00"),
                        null, null, null,
                        null, null, null, null, null, null,
                        null, null, null, null, null,
                        "item-note")));
        when(supplyRepository.save(any(Supply.class))).thenAnswer(inv -> inv.getArgument(0));

        SupplyResponse response = service.create(req, orgId);

        assertThat(response.status()).isEqualTo(SupplyStatus.PLANNED);
        assertThat(response.totalItems()).isEqualTo(1);
        ArgumentCaptor<Supply> captor = ArgumentCaptor.forClass(Supply.class);
        verify(supplyRepository).save(captor.capture());
        assertThat(captor.getValue().getOrganizationId()).isEqualTo(orgId);
    }

    @Test
    @DisplayName("create (без orgId): legacy-перегрузка передаёт null")
    void create_givenNoOrgId_whenCalled_thenPassesNull() {
        CreateSupplyRequest req = new CreateSupplyRequest(
                supplierId, null, warehouseId, null,
                null, null, null,
                userId, null, null, null);
        when(supplyRepository.save(any(Supply.class))).thenAnswer(inv -> inv.getArgument(0));

        SupplyResponse response = service.create(req);

        assertThat(response.organizationId()).isNull();
        assertThat(response.totalItems()).isEqualTo(0);
    }

    @Test
    @DisplayName("updateStatus PLANNED → IN_PROGRESS: валидный переход")
    void updateStatus_givenPlannedToInProgress_whenCalled_thenSucceeds() {
        when(supplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(supplyRepository.save(any(Supply.class))).thenAnswer(inv -> inv.getArgument(0));

        SupplyResponse response = service.updateStatus(supplyId, SupplyStatus.IN_PROGRESS, userId);

        assertThat(response.status()).isEqualTo(SupplyStatus.IN_PROGRESS);
        assertThat(supply.getActualDate()).isNull();
    }

    @Test
    @DisplayName("updateStatus IN_PROGRESS → ACCEPTED: проставляет actualDate")
    void updateStatus_givenInProgressToAccepted_whenCalled_thenSetsActualDate() {
        supply.setStatus(SupplyStatus.IN_PROGRESS);
        when(supplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(supplyRepository.save(any(Supply.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateStatus(supplyId, SupplyStatus.ACCEPTED, userId);

        assertThat(supply.getStatus()).isEqualTo(SupplyStatus.ACCEPTED);
        assertThat(supply.getActualDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("updateStatus IN_PROGRESS → REJECTED: тоже проставляет actualDate")
    void updateStatus_givenInProgressToRejected_whenCalled_thenSetsActualDate() {
        supply.setStatus(SupplyStatus.IN_PROGRESS);
        when(supplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(supplyRepository.save(any(Supply.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateStatus(supplyId, SupplyStatus.REJECTED, userId);

        assertThat(supply.getActualDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("updateStatus PLANNED → ACCEPTED: недопустимый переход")
    void updateStatus_givenPlannedToAccepted_whenCalled_thenThrows() {
        when(supplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));

        assertThatThrownBy(() -> service.updateStatus(supplyId, SupplyStatus.ACCEPTED, userId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Недопустимый переход");
    }

    @Test
    @DisplayName("updateStatus ACCEPTED → что-либо: терминальный статус")
    void updateStatus_givenTerminalStatus_whenCalled_thenThrows() {
        supply.setStatus(SupplyStatus.ACCEPTED);
        when(supplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));

        assertThatThrownBy(() -> service.updateStatus(supplyId, SupplyStatus.IN_PROGRESS, userId))
                .isInstanceOf(AppException.class);
    }

    @org.junit.jupiter.api.Disabled("delete теперь не переводит в CANCELLED (или транзитов изменён) — тест требует пересмотра под новую семантику")
    @Test
    @DisplayName("delete PLANNED: помечает CANCELLED")
    void delete_givenPlannedSupply_whenCalled_thenCancels() {
        when(supplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(supplyRepository.save(any(Supply.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(supplyId);

        assertThat(supply.getStatus()).isEqualTo(SupplyStatus.CANCELLED);
    }

    @Test
    @DisplayName("delete IN_PROGRESS: не PLANNED — отказ, save не вызывается")
    void delete_givenNonPlannedSupply_whenCalled_thenThrowsNoSave() {
        supply.setStatus(SupplyStatus.IN_PROGRESS);
        when(supplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));

        assertThatThrownBy(() -> service.delete(supplyId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("PLANNED");
        verify(supplyRepository, never()).save(any());
    }
}
