package by.bsuir.warehouseservice.integration;

import by.bsuir.warehouseservice.dto.request.CreateWarehouseRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Warehouse CRUD — E2E (Testcontainers Postgres)")
@Import(TestcontainersIntegrationBase.RabbitMocks.class)
class WarehouseCrudContainerTest extends TestcontainersIntegrationBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/warehouses без X-User-Role → 403")
    void create_GivenMissingRole_ShouldReturnForbidden() throws Exception {
        CreateWarehouseRequest req = new CreateWarehouseRequest(
                UUID.randomUUID(), "Склад 1", "Минск, ул. Тестовая 1", null
        );
        mockMvc.perform(post("/api/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST → GET: DIRECTOR создаёт склад, потом получает его обратно")
    void createThenGet_ShouldRoundTrip() throws Exception {
        UUID orgId = UUID.randomUUID();
        CreateWarehouseRequest req = new CreateWarehouseRequest(
                orgId, "Склад «Восточный»", "Минск, пр-т Партизанский 8", null
        );

        String body = mockMvc.perform(post("/api/warehouses")
                        .header("X-User-Role", "DIRECTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warehouseId").exists())
                .andExpect(jsonPath("$.name").value("Склад «Восточный»"))
                .andReturn().getResponse().getContentAsString();

        String warehouseId = objectMapper.readTree(body).get("warehouseId").asText();

        mockMvc.perform(get("/api/warehouses/{id}", warehouseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Склад «Восточный»"))
                .andExpect(jsonPath("$.address").value("Минск, пр-т Партизанский 8"));
    }

    @Test
    @DisplayName("GET /api/warehouses/organization/{orgId}: видим только склады своей организации")
    void getByOrg_ShouldIsolateByTenant() throws Exception {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/warehouses")
                            .header("X-User-Role", "DIRECTOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new CreateWarehouseRequest(orgA, "A-" + i, "addr-A-" + i, null))))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/warehouses")
                        .header("X-User-Role", "DIRECTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateWarehouseRequest(orgB, "B-0", "addr-B-0", null))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/warehouses/organization/{orgId}", orgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get("/api/warehouses/organization/{orgId}", orgB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
