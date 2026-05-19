package by.bsuir.productservice.integration;

import by.bsuir.productservice.client.DocumentClient;
import by.bsuir.productservice.client.WarehouseClient;
import by.bsuir.productservice.dto.request.CreateShipmentRequestRequest;
import by.bsuir.productservice.dto.request.PickRequest;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.InventoryEvent;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.enums.AllocationStrategy;
import by.bsuir.productservice.model.enums.InventoryStatus;
import by.bsuir.productservice.repository.InventoryEventRepository;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import by.bsuir.productservice.repository.ShipmentRequestItemRepository;
import by.bsuir.productservice.repository.ShipmentRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Ship Saga (pick → complete) — E2E (Testcontainers Postgres)")
@Import(TestcontainersIntegrationBase.RabbitMocks.class)
class ShipSagaFullContainerTest extends TestcontainersIntegrationBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductReadModelRepository productRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private InventoryEventRepository inventoryEventRepository;
    @Autowired private ProductOperationRepository operationRepository;
    @Autowired private ShipmentRequestRepository requestRepository;
    @Autowired private ShipmentRequestItemRepository itemRepository;

    @MockBean private WarehouseClient warehouseClient;
    @MockBean private DocumentClient documentClient;

    @Test
    @DisplayName("Полный цикл: create → pick → complete → quantity-10, событие ITEM_REMOVED, status COMPLETED")
    void shipSaga_FullCycle_ShouldDecrementInventoryAndEmitEvent() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        String unitSku = "PROD-001-A";

        ProductReadModel product = ProductReadModel.builder()
                .productId(UUID.randomUUID())
                .name("Хлеб ржаной")
                .sku("BREAD-RYE")
                .organizationId(orgId)
                .price(new BigDecimal("3.50"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        productRepository.save(product);

        Inventory existing = Inventory.builder()
                .inventoryId(UUID.randomUUID())
                .productId(product.getProductId())
                .organizationId(orgId)
                .warehouseId(warehouseId)
                .unitSku(unitSku)
                .quantity(new BigDecimal("100"))
                .reservedQuantity(BigDecimal.ZERO)
                .status(InventoryStatus.AVAILABLE)
                .lastUpdated(LocalDateTime.now())
                .build();
        inventoryRepository.save(existing);

        CreateShipmentRequestRequest createReq = new CreateShipmentRequestRequest(
                warehouseId, "ООО «Покупатель»", "Минск, ул. Тест 1", "987654321",
                null, "проверка цикла", AllocationStrategy.AUTO,
                null, null, null, null, null, null,
                List.of(new CreateShipmentRequestRequest.Item(
                        product.getProductId(), null, new BigDecimal("10")))
        );

        String createBody = mockMvc.perform(post("/api/operations/ship-requests")
                        .header("X-User-Id", userId.toString())
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andReturn().getResponse().getContentAsString();
        UUID requestId = UUID.fromString(objectMapper.readTree(createBody).get("requestId").asText());

        PickRequest pickReq = new PickRequest(unitSku, new BigDecimal("10"));
        mockMvc.perform(post("/api/operations/ship-requests/{id}/pick", requestId)
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pickReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKING"));

        var pickedItem = itemRepository.findByRequestId(requestId).get(0);
        assertThat(pickedItem.getPickedQty()).isEqualByComparingTo("10");
        assertThat(pickedItem.getStatus()).isEqualTo("PICKED");

        mockMvc.perform(post("/api/operations/ship-requests/{id}/complete", requestId)
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentTypes\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        var inv = inventoryRepository.findById(existing.getInventoryId()).orElseThrow();
        assertThat(inv.getQuantity()).isEqualByComparingTo("90");

        List<ProductOperation> ops = operationRepository.findAll();
        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).getOperationType().name()).isEqualTo("SHIPMENT");
        assertThat(ops.get(0).getQuantity()).isEqualByComparingTo("10");

        List<InventoryEvent> events = inventoryEventRepository
                .findByInventoryIdOrderByCreatedAtAsc(existing.getInventoryId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("ITEM_REMOVED");
        assertThat(events.get(0).getEventData().get("quantityDelta").decimalValue())
                .isEqualByComparingTo("-10");
        assertThat(events.get(0).getEventData().get("requestId").asText())
                .isEqualTo(requestId.toString());

        var finalRequest = requestRepository.findById(requestId).orElseThrow();
        assertThat(finalRequest.getStatus().name()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Pick без unitSku → 400")
    void pick_GivenNoUnitSku_ShouldReturnBadRequest() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        CreateShipmentRequestRequest createReq = new CreateShipmentRequestRequest(
                warehouseId, null, null, null, null, null, AllocationStrategy.AUTO,
                null, null, null, null, null, null,
                List.of(new CreateShipmentRequestRequest.Item(productId, null, new BigDecimal("1")))
        );
        String createBody = mockMvc.perform(post("/api/operations/ship-requests")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID requestId = UUID.fromString(objectMapper.readTree(createBody).get("requestId").asText());

        PickRequest pickReq = new PickRequest(null, new BigDecimal("1"));
        mockMvc.perform(post("/api/operations/ship-requests/{id}/pick", requestId)
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pickReq)))
                .andExpect(status().isBadRequest());
    }
}
