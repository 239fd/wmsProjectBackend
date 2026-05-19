package by.bsuir.productservice.integration;

import by.bsuir.productservice.client.DocumentClient;
import by.bsuir.productservice.client.WarehouseClient;
import by.bsuir.productservice.dto.request.ReceiveProductRequest;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.InventoryEvent;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.repository.InventoryEventRepository;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Receive operation — E2E (Testcontainers Postgres)")
@Import(TestcontainersIntegrationBase.RabbitMocks.class)
class ReceiveOperationContainerTest extends TestcontainersIntegrationBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductReadModelRepository productRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private InventoryEventRepository inventoryEventRepository;
    @Autowired private ProductOperationRepository operationRepository;

    @MockBean private WarehouseClient warehouseClient;
    @MockBean private DocumentClient documentClient;

    @Test
    @DisplayName("POST /api/operations/receive: создаёт inventory + operation + событие ITEM_ADDED")
    void receive_GivenValidRequest_ShouldPersistInventoryOperationAndEvent() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ProductReadModel product = ProductReadModel.builder()
                .productId(UUID.randomUUID())
                .name("Чай чёрный")
                .sku("TEA-001-" + UUID.randomUUID())
                .organizationId(orgId)
                .price(new BigDecimal("12.50"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        productRepository.save(product);

        when(documentClient.fetch(any(), any(), any(), any())).thenReturn(new DocumentClient.Fetched(new byte[0], "auto"));

        ReceiveProductRequest request = new ReceiveProductRequest(
                product.getProductId(), null, warehouseId, cellId,
                new BigDecimal("100"), userId, null, "первая партия"
        );

        mockMvc.perform(post("/api/operations/receive")
                        .header("X-User-Role", "WORKER")
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operationId").exists());

        List<Inventory> invList = inventoryRepository.findByWarehouseId(warehouseId);
        assertThat(invList).hasSize(1);
        Inventory inv = invList.get(0);
        assertThat(inv.getProductId()).isEqualTo(product.getProductId());
        assertThat(inv.getCellId()).isEqualTo(cellId);
        assertThat(inv.getQuantity()).isEqualByComparingTo("100");
        assertThat(inv.getOrganizationId()).isEqualTo(orgId);

        List<ProductOperation> ops = operationRepository.findAll();
        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).getOperationType().name()).isEqualTo("RECEIPT");
        assertThat(ops.get(0).getQuantity()).isEqualByComparingTo("100");

        List<InventoryEvent> events = inventoryEventRepository.findByInventoryIdOrderByCreatedAtAsc(inv.getInventoryId());
        assertThat(events).hasSize(1);
        InventoryEvent event = events.get(0);
        assertThat(event.getEventType()).isEqualTo("ITEM_ADDED");
        assertThat(event.getEventVersion()).isEqualTo(1);
        assertThat(event.getEventData().get("quantityDelta").decimalValue()).isEqualByComparingTo("100");

        mockMvc.perform(get("/api/inventory/{id}/history", inv.getInventoryId())
                        .header("X-User-Role", "DIRECTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("ITEM_ADDED"))
                .andExpect(jsonPath("$[0].eventVersion").value(1));
    }
}
