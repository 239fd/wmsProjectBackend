package by.bsuir.productservice.integration;

import by.bsuir.productservice.client.DocumentClient;
import by.bsuir.productservice.client.WarehouseClient;
import by.bsuir.productservice.dto.request.ReceiveProductRequest;
import by.bsuir.productservice.dto.request.WriteOffRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Write-off operation — E2E (Testcontainers Postgres)")
@Import(TestcontainersIntegrationBase.RabbitMocks.class)
class WriteOffOperationContainerTest extends TestcontainersIntegrationBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductReadModelRepository productRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private InventoryEventRepository inventoryEventRepository;
    @Autowired private ProductOperationRepository operationRepository;

    @MockBean private WarehouseClient warehouseClient;
    @MockBean private DocumentClient documentClient;

    @Test
    @DisplayName("Receive → write-off: quantity -15, событие WRITTEN_OFF v2 c reason")
    void receiveThenWriteOff_ShouldDecrementAndEmitEvent() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ProductReadModel product = ProductReadModel.builder()
                .productId(UUID.randomUUID())
                .name("Молоко 1л")
                .sku("MILK-1L-" + UUID.randomUUID())
                .organizationId(orgId)
                .price(new BigDecimal("2.30"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        productRepository.save(product);

        when(documentClient.generateReceiptOrder(any(), any())).thenReturn(UUID.randomUUID());
        when(documentClient.generateWriteOffAct(any(), any())).thenReturn(UUID.randomUUID());

        ReceiveProductRequest receive = new ReceiveProductRequest(
                product.getProductId(), null, warehouseId, UUID.randomUUID(),
                new BigDecimal("50"), userId, null, null
        );
        mockMvc.perform(post("/api/operations/receive")
                        .header("X-User-Role", "WORKER")
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receive)))
                .andExpect(status().isCreated());

        WriteOffRequest writeOff = new WriteOffRequest(
                product.getProductId(), warehouseId, null, null,
                new BigDecimal("15"),
                "Просрочка", "Акт #42", null, List.of(),
                userId, "санитарный контроль"
        );
        mockMvc.perform(post("/api/operations/write-off")
                        .header("X-User-Role", "ACCOUNTANT")
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(writeOff)))
                .andExpect(status().isCreated());

        Inventory inv = inventoryRepository.findByWarehouseId(warehouseId).get(0);
        assertThat(inv.getQuantity()).isEqualByComparingTo("35");

        List<ProductOperation> ops = operationRepository.findAll();
        assertThat(ops).hasSize(2);
        assertThat(ops).extracting(o -> o.getOperationType().name())
                .containsExactlyInAnyOrder("RECEIPT", "WRITE_OFF");

        List<InventoryEvent> events = inventoryEventRepository
                .findByInventoryIdOrderByCreatedAtAsc(inv.getInventoryId());
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getEventType()).isEqualTo("ITEM_ADDED");
        assertThat(events.get(0).getEventVersion()).isEqualTo(1);
        assertThat(events.get(1).getEventType()).isEqualTo("WRITTEN_OFF");
        assertThat(events.get(1).getEventVersion()).isEqualTo(2);
        assertThat(events.get(1).getEventData().get("reason").asText()).isEqualTo("Просрочка");
        assertThat(events.get(1).getEventData().get("quantityDelta").decimalValue())
                .isEqualByComparingTo("-15");
    }

    @Test
    @DisplayName("Write-off без X-User-Role → 403, ничего не пишется")
    void writeOff_GivenMissingRole_ShouldReturnForbidden() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        ProductReadModel product = ProductReadModel.builder()
                .productId(UUID.randomUUID())
                .name("X")
                .sku("X-" + UUID.randomUUID())
                .organizationId(orgId)
                .price(new BigDecimal("1.00"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        productRepository.save(product);

        WriteOffRequest writeOff = new WriteOffRequest(
                product.getProductId(), warehouseId, null, null,
                new BigDecimal("1"), "any", null, null, List.of(),
                UUID.randomUUID(), null
        );
        mockMvc.perform(post("/api/operations/write-off")
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(writeOff)))
                .andExpect(status().isForbidden());

        assertThat(operationRepository.count()).isZero();
    }
}
