package by.bsuir.productservice.service;

import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.InventoryCount;
import by.bsuir.productservice.model.entity.InventorySession;
import by.bsuir.productservice.model.enums.InventoryStatus;
import by.bsuir.productservice.repository.InventoryCountRepository;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.InventorySessionRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryCheckService Tests")
class InventoryCheckServiceTest {

    @Mock
    private InventorySessionRepository sessionRepository;

    @Mock
    private InventoryCountRepository countRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductOperationRepository operationRepository;

    @InjectMocks
    private InventoryCheckService inventoryCheckService;

    private UUID warehouseId;
    private UUID userId;
    private UUID sessionId;
    private UUID productId;
    private UUID cellId;
    private InventorySession session;
    private Inventory inventory;
    private InventoryCount inventoryCount;

    @BeforeEach
    void setUp() {
        warehouseId = UUID.randomUUID();
        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        productId = UUID.randomUUID();
        cellId = UUID.randomUUID();

        session = InventorySession.builder()
                .sessionId(sessionId)
                .warehouseId(warehouseId)
                .startedBy(userId)
                .startedAt(LocalDateTime.now())
                .status(InventorySession.SessionStatus.IN_PROGRESS)
                .notes("Test session")
                .build();

        inventory = Inventory.builder()
                .inventoryId(UUID.randomUUID())
                .productId(productId)
                .warehouseId(warehouseId)
                .cellId(cellId)
                .quantity(new BigDecimal("100"))
                .reservedQuantity(BigDecimal.ZERO)
                .status(InventoryStatus.AVAILABLE)
                .lastUpdated(LocalDateTime.now())
                .build();

        inventoryCount = InventoryCount.builder()
                .countId(UUID.randomUUID())
                .sessionId(sessionId)
                .productId(productId)
                .cellId(cellId)
                .expectedQuantity(new BigDecimal("100"))
                .actualQuantity(null)
                .discrepancy(BigDecimal.ZERO)
                .build();
    }

    @Test
    @DisplayName("Should start inventory check successfully")
    void shouldStartInventoryCheckSuccessfully() {
        when(sessionRepository.findByStatus(InventorySession.SessionStatus.IN_PROGRESS))
                .thenReturn(Collections.emptyList());
        when(sessionRepository.save(any(InventorySession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.findByWarehouseId(warehouseId))
                .thenReturn(Arrays.asList(inventory));
        when(countRepository.save(any(InventoryCount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UUID resultSessionId = inventoryCheckService.startInventory(warehouseId, userId, "Test notes");

        assertThat(resultSessionId).isNotNull();
        verify(sessionRepository, times(1)).save(any(InventorySession.class));
        verify(inventoryRepository, times(1)).findByWarehouseId(warehouseId);
        verify(countRepository, times(1)).save(any(InventoryCount.class));
    }

    @Test
    @DisplayName("Should throw exception when inventory already in progress")
    void shouldThrowExceptionWhenInventoryAlreadyInProgress() {
        InventorySession activeSession = InventorySession.builder()
                .sessionId(UUID.randomUUID())
                .warehouseId(warehouseId)
                .status(InventorySession.SessionStatus.IN_PROGRESS)
                .build();

        when(sessionRepository.findByStatus(InventorySession.SessionStatus.IN_PROGRESS))
                .thenReturn(Arrays.asList(activeSession));

        assertThatThrownBy(() -> inventoryCheckService.startInventory(warehouseId, userId, "Test notes"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("уже идёт");

        verify(sessionRepository, never()).save(any(InventorySession.class));
    }

    @Test
    @DisplayName("Should record actual count successfully")
    void shouldRecordActualCountSuccessfully() {
        BigDecimal actualQuantity = new BigDecimal("95");

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(countRepository.findBySessionId(sessionId)).thenReturn(Arrays.asList(inventoryCount));
        when(countRepository.save(any(InventoryCount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        inventoryCheckService.recordActualCount(sessionId, productId, cellId, actualQuantity, "Test notes");

        assertThat(inventoryCount.getActualQuantity()).isEqualTo(actualQuantity);
        assertThat(inventoryCount.getDiscrepancy()).isEqualTo(new BigDecimal("-5"));
        verify(sessionRepository, times(1)).findById(sessionId);
        verify(countRepository, times(1)).save(any(InventoryCount.class));
    }

    @Test
    @DisplayName("Should throw exception when session not found for recording count")
    void shouldThrowExceptionWhenSessionNotFoundForRecordingCount() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryCheckService.recordActualCount(
                sessionId, productId, cellId, new BigDecimal("95"), "Test notes"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найдена");

        verify(sessionRepository, times(1)).findById(sessionId);
        verify(countRepository, never()).save(any(InventoryCount.class));
    }

    @Test
    @DisplayName("Should throw exception when session already completed")
    void shouldThrowExceptionWhenSessionAlreadyCompleted() {
        session.setStatus(InventorySession.SessionStatus.COMPLETED);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> inventoryCheckService.recordActualCount(
                sessionId, productId, cellId, new BigDecimal("95"), "Test notes"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("завершена");

        verify(sessionRepository, times(1)).findById(sessionId);
        verify(countRepository, never()).save(any(InventoryCount.class));
    }

    @Test
    @DisplayName("Should throw exception when count record not found")
    void shouldThrowExceptionWhenCountRecordNotFound() {
        UUID nonExistentProductId = UUID.randomUUID();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(countRepository.findBySessionId(sessionId)).thenReturn(Arrays.asList(inventoryCount));

        assertThatThrownBy(() -> inventoryCheckService.recordActualCount(
                sessionId, nonExistentProductId, cellId, new BigDecimal("95"), "Test notes"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найдена");

        verify(countRepository, never()).save(any(InventoryCount.class));
    }

    @Test
    @DisplayName("Should complete inventory check successfully")
    void shouldCompleteInventoryCheckSuccessfully() {
        inventoryCount.setActualQuantity(new BigDecimal("100"));
        inventoryCount.setDiscrepancy(BigDecimal.ZERO);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(countRepository.findBySessionId(sessionId)).thenReturn(Arrays.asList(inventoryCount));
        when(sessionRepository.save(any(InventorySession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = inventoryCheckService.completeInventory(sessionId, userId);

        assertThat(result).isNotNull();
        assertThat(result.get("sessionId")).isEqualTo(sessionId.toString());
        assertThat(result.get("totalRecords")).isEqualTo(1);
        assertThat(result.get("discrepanciesCount")).isEqualTo(0);
        verify(sessionRepository, times(1)).findById(sessionId);
        verify(countRepository, times(1)).findBySessionId(sessionId);
        verify(sessionRepository, times(1)).save(any(InventorySession.class));
    }
}

