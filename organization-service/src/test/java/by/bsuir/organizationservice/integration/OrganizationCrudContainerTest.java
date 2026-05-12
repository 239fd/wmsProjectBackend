package by.bsuir.organizationservice.integration;

import by.bsuir.organizationservice.dto.request.CreateOrganizationRequest;
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

@DisplayName("Organization CRUD — E2E (Testcontainers Postgres)")
@Import(TestcontainersIntegrationBase.RabbitMocks.class)
class OrganizationCrudContainerTest extends TestcontainersIntegrationBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/organizations с ролью DIRECTOR → 201, организация создаётся")
    void create_GivenDirector_ShouldPersist() throws Exception {
        CreateOrganizationRequest req = new CreateOrganizationRequest(
                "ОАО «Тестовый завод»", "Тестзавод", "123456789", "Минск, ул. Промышленная 1"
        );

        String body = mockMvc.perform(post("/api/organizations")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "DIRECTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").exists())
                .andExpect(jsonPath("$.name").value("ОАО «Тестовый завод»"))
                .andExpect(jsonPath("$.unp").value("123456789"))
                .andReturn().getResponse().getContentAsString();

        String orgId = objectMapper.readTree(body).get("organizationId").asText();

        mockMvc.perform(get("/api/organizations/{id}", orgId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "DIRECTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ОАО «Тестовый завод»"));
    }

    @Test
    @DisplayName("POST /api/organizations не-DIRECTOR (WORKER) → 403")
    void create_GivenNonDirector_ShouldReturnForbidden() throws Exception {
        CreateOrganizationRequest req = new CreateOrganizationRequest(
                "X", "X", "987654321", "addr"
        );
        mockMvc.perform(post("/api/organizations")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST с невалидным ИНН (не 9 цифр) → 400")
    void create_GivenInvalidUnp_ShouldReturnBadRequest() throws Exception {
        CreateOrganizationRequest req = new CreateOrganizationRequest(
                "X", "X", "12345", "addr"
        );
        mockMvc.perform(post("/api/organizations")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "DIRECTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
