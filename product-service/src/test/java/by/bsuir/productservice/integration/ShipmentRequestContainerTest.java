package by.bsuir.productservice.integration;

import by.bsuir.productservice.client.DocumentClient;
import by.bsuir.productservice.client.WarehouseClient;
import by.bsuir.productservice.dto.request.CreateShipmentRequestRequest;
import by.bsuir.productservice.model.enums.AllocationStrategy;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Shipment Request CRUD — E2E (Testcontainers Postgres)")
@Import(TestcontainersIntegrationBase.RabbitMocks.class)
class ShipmentRequestContainerTest extends TestcontainersIntegrationBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ShipmentRequestRepository requestRepository;
    @Autowired private ShipmentRequestItemRepository itemRepository;

    @MockBean private WarehouseClient warehouseClient;
    @MockBean private DocumentClient documentClient;

    @Test
    @DisplayName("POST /ship-requests: создаёт заявку со статусом PLANNED + items с pickedQty=0")
    void create_ShouldPersistRequestAndItems() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID productA = UUID.randomUUID();
        UUID productB = UUID.randomUUID();

        CreateShipmentRequestRequest req = new CreateShipmentRequestRequest(
                warehouseId,
                "ООО «Покупатель»", "Минск, ул. Получателей 5", "987654321",
                LocalDate.now().plusDays(3), "тестовая заявка",
                AllocationStrategy.AUTO,
                List.of(
                        new CreateShipmentRequestRequest.Item(productA, null, new BigDecimal("10")),
                        new CreateShipmentRequestRequest.Item(productB, null, new BigDecimal("5"))
                )
        );

        String body = mockMvc.perform(post("/api/operations/ship-requests")
                        .header("X-User-Id", userId.toString())
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andReturn().getResponse().getContentAsString();

        UUID requestId = UUID.fromString(objectMapper.readTree(body).get("requestId").asText());

        assertThat(requestRepository.findById(requestId)).isPresent();
        var items = itemRepository.findByRequestId(requestId);
        assertThat(items).hasSize(2);
        assertThat(items).allSatisfy(i -> {
            assertThat(i.getPickedQty()).isEqualByComparingTo("0");
            assertThat(i.getStatus()).isEqualTo("PENDING");
        });
    }

    @Test
    @DisplayName("GET /ship-requests изолирован по X-Organization-Id")
    void list_ShouldFilterByOrg() throws Exception {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();

        for (int i = 0; i < 2; i++) {
            createRequestFor(orgA);
        }
        createRequestFor(orgB);

        mockMvc.perform(get("/api/operations/ship-requests")
                        .header("X-Organization-Id", orgA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/operations/ship-requests")
                        .header("X-Organization-Id", orgB.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("DELETE /ship-requests/{id}: отменяет заявку (204)")
    void cancel_ShouldReturnNoContent() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID requestId = createRequestFor(orgId);

        mockMvc.perform(delete("/api/operations/ship-requests/{id}", requestId)
                        .header("X-Organization-Id", orgId.toString()))
                .andExpect(status().isNoContent());

        var entity = requestRepository.findById(requestId).orElseThrow();
        assertThat(entity.getStatus().name()).isEqualTo("CANCELLED");
    }

    private UUID createRequestFor(UUID orgId) throws Exception {
        CreateShipmentRequestRequest req = new CreateShipmentRequestRequest(
                UUID.randomUUID(), null, null, null, null, null,
                AllocationStrategy.AUTO,
                List.of(new CreateShipmentRequestRequest.Item(
                        UUID.randomUUID(), null, new BigDecimal("1")))
        );
        String body = mockMvc.perform(post("/api/operations/ship-requests")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("requestId").asText());
    }
}
