package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.CreateSupplierRequest;
import by.bsuir.productservice.dto.response.SupplierResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Supplier;
import by.bsuir.productservice.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
@DisplayName("SupplierService Tests")
class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    @InjectMocks
    private SupplierService service;

    private UUID orgId;
    private UUID supplierId;
    private Supplier supplier;
    private CreateSupplierRequest request;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        supplierId = UUID.randomUUID();
        supplier = Supplier.builder()
                .supplierId(supplierId)
                .organizationId(orgId)
                .name("ОАО Молоко")
                .unp("100234567")
                .contactPerson("Иванов И.И.")
                .phone("+375291234567")
                .email("a@b.by")
                .address("Минск")
                .isActive(true)
                .build();
        request = new CreateSupplierRequest(
                "ОАО Молоко", "100234567", "Иванов И.И.",
                "+375291234567", "a@b.by", "Минск");
    }

    @Test
    @DisplayName("getAll(orgId): фильтрует по организации и активности")
    void getAll_givenOrgId_whenCalled_thenFiltersByOrgAndActive() {
        when(supplierRepository.findByOrganizationIdAndIsActiveTrue(orgId))
                .thenReturn(List.of(supplier));

        List<SupplierResponse> result = service.getAll(orgId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).supplierId()).isEqualTo(supplierId);
        verify(supplierRepository).findByOrganizationIdAndIsActiveTrue(orgId);
    }

    @Test
    @DisplayName("getAll(null): без orgId — все активные")
    void getAll_givenNullOrgId_whenCalled_thenAllActive() {
        when(supplierRepository.findByIsActiveTrue()).thenReturn(List.of(supplier));

        List<SupplierResponse> result = service.getAll(null);

        assertThat(result).hasSize(1);
        verify(supplierRepository).findByIsActiveTrue();
    }

    @Test
    @DisplayName("getAll(orgId, Pageable): пагинированный ответ")
    void getAll_givenOrgIdAndPageable_whenCalled_thenReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(supplierRepository.findByOrganizationIdAndIsActiveTrue(eq(orgId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(supplier), pageable, 1));

        var page = service.getAll(orgId, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1L);
        assertThat(page.getContent().get(0).supplierId()).isEqualTo(supplierId);
    }

    @Test
    @DisplayName("getAll(null, Pageable): без orgId — все активные с пагинацией")
    void getAll_givenNullOrgIdAndPageable_whenCalled_thenReturnsAllActivePage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(supplierRepository.findByIsActiveTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(supplier), pageable, 1));

        var page = service.getAll(null, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getById: возвращает по id и проверяет orgId")
    void getById_givenExistingSupplier_whenCalled_thenReturnsResponse() {
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));

        SupplierResponse response = service.getById(supplierId, orgId);

        assertThat(response.supplierId()).isEqualTo(supplierId);
        assertThat(response.name()).isEqualTo("ОАО Молоко");
    }

    @Test
    @DisplayName("getById: чужая org → forbidden")
    void getById_givenOtherOrg_whenCalled_thenThrowsForbidden() {
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));

        assertThatThrownBy(() -> service.getById(supplierId, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("другой организации");
    }

    @Test
    @DisplayName("getById: не найден → notFound")
    void getById_givenMissingSupplier_whenCalled_thenThrowsNotFound() {
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(supplierId, orgId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найден");
    }

    @Test
    @DisplayName("getById: неактивный (is_active=false) → notFound (фильтруется)")
    void getById_givenInactiveSupplier_whenCalled_thenThrowsNotFound() {
        supplier.setIsActive(false);
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));

        assertThatThrownBy(() -> service.getById(supplierId, orgId))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("create: новый поставщик сохраняется")
    void create_givenValidRequest_whenCalled_thenSavesAndReturnsResponse() {
        when(supplierRepository.existsByOrganizationIdAndUnpAndIsActiveTrue(orgId, "100234567"))
                .thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));

        SupplierResponse response = service.create(request, orgId);

        assertThat(response.name()).isEqualTo("ОАО Молоко");
        verify(supplierRepository).save(any(Supplier.class));
    }

    @Test
    @DisplayName("create: дубль УНП в той же org → conflict")
    void create_givenDuplicateUnp_whenCalled_thenThrowsConflict() {
        when(supplierRepository.existsByOrganizationIdAndUnpAndIsActiveTrue(orgId, "100234567"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(request, orgId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("УНП");
        verify(supplierRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: пустой УНП не проверяется на конфликт")
    void create_givenBlankUnp_whenCalled_thenSkipsConflictCheck() {
        CreateSupplierRequest blankUnp = new CreateSupplierRequest(
                "Поставщик", "", "к", "ф", "e", "a");
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));

        SupplierResponse response = service.create(blankUnp, orgId);

        assertThat(response.name()).isEqualTo("Поставщик");
        verify(supplierRepository, never()).existsByOrganizationIdAndUnpAndIsActiveTrue(any(), any());
    }

    @Test
    @DisplayName("create: orgId=null проверяет глобально по УНП")
    void create_givenNullOrgIdAndUnp_whenCalled_thenChecksGlobalUnp() {
        when(supplierRepository.existsByUnpAndIsActiveTrue("100234567")).thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(request, null);

        verify(supplierRepository).existsByUnpAndIsActiveTrue("100234567");
    }

    @Test
    @DisplayName("update: меняет поля и сохраняет")
    void update_givenValidRequest_whenCalled_thenUpdatesFields() {
        CreateSupplierRequest updateReq = new CreateSupplierRequest(
                "Новое имя", "100234567", "Петров", "+1", "p@p.by", "Гомель");
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));

        SupplierResponse response = service.update(supplierId, updateReq, orgId);

        assertThat(response.name()).isEqualTo("Новое имя");
        assertThat(supplier.getContactPerson()).isEqualTo("Петров");
        assertThat(supplier.getAddress()).isEqualTo("Гомель");
    }

    @Test
    @DisplayName("update: смена УНП на чужой существующий → conflict")
    void update_givenNewUnpThatExists_whenCalled_thenThrowsConflict() {
        CreateSupplierRequest updateReq = new CreateSupplierRequest(
                "Новое имя", "999999999", "Петров", "+1", "p@p.by", "Гомель");
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
        when(supplierRepository.existsByOrganizationIdAndUnpAndIsActiveTrue(orgId, "999999999"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(supplierId, updateReq, orgId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("УНП");
    }

    @Test
    @DisplayName("update: УНП не изменился — конфликт не проверяется")
    void update_givenSameUnp_whenCalled_thenSkipsConflictCheck() {
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(supplierId, request, orgId);

        verify(supplierRepository, never()).existsByOrganizationIdAndUnpAndIsActiveTrue(any(), any());
    }

    @Test
    @DisplayName("delete: помечает is_active=false и сохраняет")
    void delete_givenExistingSupplier_whenCalled_thenSoftDeletes() {
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(supplierId, orgId);

        assertThat(supplier.getIsActive()).isFalse();
        verify(supplierRepository).save(supplier);
    }

    @Test
    @DisplayName("delete: чужая org → forbidden, save не вызывается")
    void delete_givenOtherOrg_whenCalled_thenForbiddenNoSave() {
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));

        assertThatThrownBy(() -> service.delete(supplierId, UUID.randomUUID()))
                .isInstanceOf(AppException.class);
        verify(supplierRepository, never()).save(any());
    }
}
