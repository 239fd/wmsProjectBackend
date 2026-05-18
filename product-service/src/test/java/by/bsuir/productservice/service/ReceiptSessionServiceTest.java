package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.CreateReceiptSessionRequest;
import by.bsuir.productservice.dto.request.ReceiveProductRequest;
import by.bsuir.productservice.dto.request.SessionDiscrepancyRequest;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.GeneratedDocument;
import by.bsuir.productservice.model.entity.ProductBatch;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.entity.ReceiptSession;
import by.bsuir.productservice.model.entity.Supplier;
import by.bsuir.productservice.model.enums.OperationStatus;
import by.bsuir.productservice.model.enums.ReceiptSessionStatus;
import by.bsuir.productservice.repository.ProductBatchRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import by.bsuir.productservice.repository.ReceiptSessionRepository;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReceiptSessionService Tests")
class ReceiptSessionServiceTest {

    @Mock
    private ReceiptSessionRepository sessionRepository;
    @Mock
    private ProductOperationService productOperationService;
    @Mock
    private ProductOperationRepository operationRepository;
    @Mock
    private ProductReadModelRepository productRepository;
    @Mock
    private ProductBatchRepository batchRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private DocumentRegistryService documentRegistryService;

    @InjectMocks
    private ReceiptSessionService service;

    private UUID orgId;
    private UUID warehouseId;
    private UUID userId;
    private UUID supplierId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        userId = UUID.randomUUID();
        supplierId = UUID.randomUUID();
        productId = UUID.randomUUID();
    }

    private CreateReceiptSessionRequest baseRequest(List<CreateReceiptSessionRequest.ReceiptItem> items) {
        return new CreateReceiptSessionRequest(
                warehouseId, supplierId, null, userId, "note", items);
    }

    private CreateReceiptSessionRequest.ReceiptItem item(UUID batchId, String batchNumber) {
        return new CreateReceiptSessionRequest.ReceiptItem(
                productId, batchId, UUID.randomUUID(),
                new BigDecimal("10"), new BigDecimal("2.00"),
                batchNumber, LocalDate.now().plusMonths(6), "i-note");
    }

    @Test
    @DisplayName("createSession: успешный happy-path с готовым batchId")
    void createSession_givenValidRequest_whenCalled_thenSavesSessionAndRegistersDocs() {
        var req = baseRequest(List.of(item(UUID.randomUUID(), null)));
        UUID opId = UUID.randomUUID();
        when(productOperationService.receiveItemInSession(any(ReceiveProductRequest.class), eq(orgId), any(UUID.class)))
                .thenReturn(opId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(
                ProductReadModel.builder().productId(productId).name("Молоко").sku("MLK").unitOfMeasure("л").build()));
        when(documentRegistryService.register(any(), eq("receipt-order"), any(), eq(orgId), eq(userId)))
                .thenReturn(GeneratedDocument.builder().id(UUID.randomUUID()).documentNumber("ПО-2026-1").build());
        when(documentRegistryService.register(any(), eq("receipt-act"), any(), eq(orgId), eq(userId)))
                .thenReturn(GeneratedDocument.builder().id(UUID.randomUUID()).documentNumber("АП-2026-1").build());
        when(sessionRepository.save(any(ReceiptSession.class))).thenAnswer(inv -> inv.getArgument(0));

        ReceiptSession session = service.createSession(req, orgId);

        assertThat(session.getStatus()).isEqualTo(ReceiptSessionStatus.PAUSED);
        assertThat(session.getReceiptOrderDocId()).isNotNull();
        assertThat(session.getReceiptActDocId()).isNotNull();
        verify(productOperationService).receiveItemInSession(any(), eq(orgId), any(UUID.class));
    }

    @Test
    @DisplayName("createSession: orgId=null → badRequest")
    void createSession_givenNullOrgId_whenCalled_thenThrows() {
        assertThatThrownBy(() ->
                service.createSession(baseRequest(List.of(item(null, null))), null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("X-Organization-Id");
    }

    @Test
    @DisplayName("createSession: пустой список позиций → badRequest")
    void createSession_givenEmptyItems_whenCalled_thenThrows() {
        assertThatThrownBy(() ->
                service.createSession(baseRequest(List.of()), orgId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("позиций");
    }

    @Test
    @DisplayName("createSession: автосоздание ProductBatch если batchId null но batchNumber задан")
    void createSession_givenBatchNumberWithoutId_whenCalled_thenAutoCreatesBatch() {
        var req = baseRequest(List.of(item(null, "BATCH-001")));
        UUID newBatchId = UUID.randomUUID();
        when(productOperationService.receiveItemInSession(any(), eq(orgId), any())).thenReturn(UUID.randomUUID());
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(
                Supplier.builder().supplierId(supplierId).name("Поставщик-X").build()));
        when(batchRepository.save(any(ProductBatch.class))).thenAnswer(inv -> {
            ProductBatch b = inv.getArgument(0);
            b.setBatchId(newBatchId);
            return b;
        });
        when(sessionRepository.save(any(ReceiptSession.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createSession(req, orgId);

        verify(batchRepository).save(any(ProductBatch.class));
    }

    @Test
    @DisplayName("createSession: ни batchId ни batchNumber → batch не создаётся")
    void createSession_givenNoBatchInfo_whenCalled_thenSkipsBatchCreation() {
        var req = baseRequest(List.of(item(null, null)));
        when(productOperationService.receiveItemInSession(any(), eq(orgId), any())).thenReturn(UUID.randomUUID());
        when(sessionRepository.save(any(ReceiptSession.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createSession(req, orgId);

        verify(batchRepository, never()).save(any(ProductBatch.class));
    }

    @Test
    @DisplayName("createSession: документы не зарегистрировались — session всё равно создаётся")
    void createSession_givenDocumentRegistryFails_whenCalled_thenStillReturnsSession() {
        var req = baseRequest(List.of(item(UUID.randomUUID(), null)));
        when(productOperationService.receiveItemInSession(any(), eq(orgId), any())).thenReturn(UUID.randomUUID());
        when(documentRegistryService.register(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("MinIO down"));
        when(sessionRepository.save(any(ReceiptSession.class))).thenAnswer(inv -> inv.getArgument(0));

        ReceiptSession session = service.createSession(req, orgId);

        assertThat(session.getReceiptOrderDocId()).isNull();
        assertThat(session.getReceiptActDocId()).isNull();
    }

    @Test
    @DisplayName("completeSession: PAUSED → COMPLETED, операции в COMPLETED")
    void completeSession_givenPausedSession_whenCalled_thenCompletes() {
        UUID sessionId = UUID.randomUUID();
        ReceiptSession session = ReceiptSession.builder()
                .sessionId(sessionId).organizationId(orgId)
                .status(ReceiptSessionStatus.PAUSED).build();
        ProductOperation op = ProductOperation.builder()
                .operationId(UUID.randomUUID()).status(OperationStatus.PAUSED).build();
        when(sessionRepository.findBySessionIdAndOrganizationId(sessionId, orgId))
                .thenReturn(Optional.of(session));
        when(operationRepository.findBySessionId(sessionId)).thenReturn(List.of(op));
        when(sessionRepository.save(any(ReceiptSession.class))).thenAnswer(inv -> inv.getArgument(0));

        ReceiptSession result = service.completeSession(sessionId, userId, orgId);

        assertThat(result.getStatus()).isEqualTo(ReceiptSessionStatus.COMPLETED);
        assertThat(op.getStatus()).isEqualTo(OperationStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("completeSession: не PAUSED → conflict")
    void completeSession_givenWrongStatus_whenCalled_thenThrowsConflict() {
        UUID sessionId = UUID.randomUUID();
        ReceiptSession session = ReceiptSession.builder()
                .sessionId(sessionId).organizationId(orgId)
                .status(ReceiptSessionStatus.COMPLETED).build();
        when(sessionRepository.findBySessionIdAndOrganizationId(sessionId, orgId))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.completeSession(sessionId, userId, orgId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("ожидался");
    }

    @Test
    @DisplayName("completeSession: сессия не найдена → notFound")
    void completeSession_givenMissingSession_whenCalled_thenThrowsNotFound() {
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.findBySessionIdAndOrganizationId(sessionId, orgId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeSession(sessionId, userId, orgId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найдена");
    }

    @Test
    @DisplayName("recordDiscrepancy: реальные расхождения → COMPLETED_WITH_DISCREPANCY")
    void recordDiscrepancy_givenRealDiscrepancies_whenCalled_thenCompletesWithDiscrepancy() {
        UUID sessionId = UUID.randomUUID();
        ReceiptSession session = ReceiptSession.builder()
                .sessionId(sessionId).organizationId(orgId).warehouseId(warehouseId)
                .status(ReceiptSessionStatus.PAUSED).supplierId(supplierId).build();
        SessionDiscrepancyRequest req = new SessionDiscrepancyRequest(
                userId, "fix",
                List.of(new SessionDiscrepancyRequest.DiscrepancyItem(
                        productId, new BigDecimal("10"), new BigDecimal("7"),
                        "тара повреждена", "SHORTAGE")));
        when(sessionRepository.findBySessionIdAndOrganizationId(sessionId, orgId))
                .thenReturn(Optional.of(session));
        when(operationRepository.findBySessionId(sessionId)).thenReturn(List.of(
                ProductOperation.builder().operationId(UUID.randomUUID()).status(OperationStatus.PAUSED).build()));
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(
                Supplier.builder().supplierId(supplierId).name("Поставщик-X").unp("100").address("Минск").build()));
        when(productRepository.findById(productId)).thenReturn(Optional.of(
                ProductReadModel.builder().productId(productId).name("Молоко").sku("MLK").build()));
        when(documentRegistryService.register(any(), eq("receipt-act"), any(), eq(orgId), eq(userId)))
                .thenReturn(GeneratedDocument.builder().id(UUID.randomUUID()).documentNumber("АП-2026-99").build());
        when(sessionRepository.save(any(ReceiptSession.class))).thenAnswer(inv -> inv.getArgument(0));

        ReceiptSession result = service.recordDiscrepancy(sessionId, req, orgId);

        assertThat(result.getStatus()).isEqualTo(ReceiptSessionStatus.COMPLETED_WITH_DISCREPANCY);
        assertThat(result.getCompletedAt()).isNotNull();
        assertThat(result.getReceiptActDocId()).isNotNull();
    }

    @Test
    @DisplayName("recordDiscrepancy: actualQty==expectedQty → badRequest «Расхождений нет»")
    void recordDiscrepancy_givenEqualQuantities_whenCalled_thenThrowsBadRequest() {
        UUID sessionId = UUID.randomUUID();
        ReceiptSession session = ReceiptSession.builder()
                .sessionId(sessionId).organizationId(orgId)
                .status(ReceiptSessionStatus.PAUSED).build();
        SessionDiscrepancyRequest req = new SessionDiscrepancyRequest(
                userId, null,
                List.of(new SessionDiscrepancyRequest.DiscrepancyItem(
                        productId, new BigDecimal("10"), new BigDecimal("10"),
                        null, null)));
        when(sessionRepository.findBySessionIdAndOrganizationId(sessionId, orgId))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.recordDiscrepancy(sessionId, req, orgId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Расхождений нет");
    }

    @Test
    @DisplayName("recordDiscrepancy: не PAUSED → conflict")
    void recordDiscrepancy_givenWrongStatus_whenCalled_thenThrowsConflict() {
        UUID sessionId = UUID.randomUUID();
        ReceiptSession session = ReceiptSession.builder()
                .sessionId(sessionId).organizationId(orgId)
                .status(ReceiptSessionStatus.COMPLETED).build();
        SessionDiscrepancyRequest req = new SessionDiscrepancyRequest(userId, null, List.of());
        when(sessionRepository.findBySessionIdAndOrganizationId(sessionId, orgId))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.recordDiscrepancy(sessionId, req, orgId))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("listSessions: orgId+warehouseId+status — фильтрует по 3 критериям")
    void listSessions_givenAllFilters_whenCalled_thenUsesWarehouseRepoQuery() {
        var pageable = PageRequest.of(0, 10);
        ReceiptSession session = ReceiptSession.builder()
                .sessionId(UUID.randomUUID()).organizationId(orgId).build();
        when(sessionRepository.findByOrganizationIdAndWarehouseIdAndStatus(
                eq(orgId), eq(warehouseId), eq(ReceiptSessionStatus.PAUSED), any()))
                .thenReturn(new PageImpl<>(List.of(session)));

        assertThat(service.listSessions(orgId, ReceiptSessionStatus.PAUSED, warehouseId, pageable))
                .hasSize(1);
    }

    @Test
    @DisplayName("listSessions: warehouseId=null → org+status фильтр")
    void listSessions_givenNullWarehouse_whenCalled_thenUsesOrgStatusQuery() {
        var pageable = PageRequest.of(0, 10);
        when(sessionRepository.findByOrganizationIdAndStatus(
                eq(orgId), eq(ReceiptSessionStatus.PAUSED), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listSessions(orgId, null, null, pageable);

        verify(sessionRepository).findByOrganizationIdAndStatus(eq(orgId), eq(ReceiptSessionStatus.PAUSED), any());
    }

    @Test
    @DisplayName("listSessions: orgId=null → badRequest")
    void listSessions_givenNullOrgId_whenCalled_thenThrows() {
        assertThatThrownBy(() -> service.listSessions(null, null, null, PageRequest.of(0, 10)))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("getSession: возвращает по sessionId+orgId")
    void getSession_givenValidIds_whenCalled_thenReturnsSession() {
        UUID sessionId = UUID.randomUUID();
        ReceiptSession session = ReceiptSession.builder().sessionId(sessionId).build();
        when(sessionRepository.findBySessionIdAndOrganizationId(sessionId, orgId))
                .thenReturn(Optional.of(session));

        assertThat(service.getSession(sessionId, orgId)).isSameAs(session);
    }

    @Test
    @DisplayName("getSession: orgId=null fallback на findById")
    void getSession_givenNullOrgId_whenCalled_thenUsesFindById() {
        UUID sessionId = UUID.randomUUID();
        ReceiptSession session = ReceiptSession.builder().sessionId(sessionId).build();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThat(service.getSession(sessionId, null)).isSameAs(session);
    }

    @Test
    @DisplayName("getSessionOperations: возвращает операции по sessionId")
    void getSessionOperations_givenSessionId_whenCalled_thenReturnsList() {
        UUID sessionId = UUID.randomUUID();
        ProductOperation op = ProductOperation.builder().operationId(UUID.randomUUID()).build();
        when(operationRepository.findBySessionId(sessionId)).thenReturn(List.of(op));

        assertThat(service.getSessionOperations(sessionId)).containsExactly(op);
    }
}
