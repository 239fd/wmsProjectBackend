package by.bsuir.organizationservice.integration;

import by.bsuir.organizationservice.controller.EmployeeController;
import by.bsuir.organizationservice.dto.AddEmployeeRequest;
import by.bsuir.organizationservice.dto.EmployeeResponse;
import by.bsuir.organizationservice.service.EmployeeManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты для EmployeeController.
 *
 * Тестируют веб-слой:
 * - Добавление сотрудников в организацию
 * - Удаление сотрудников
 * - Проверка ролей (только DIRECTOR может управлять сотрудниками)
 */
@WebMvcTest(controllers = EmployeeController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@DisplayName("EmployeeController Integration Tests")
class EmployeeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmployeeManagementService employeeManagementService;

    @Nested
    @DisplayName("POST /api/organizations/{orgId}/employees - Добавление сотрудника")
    class AddEmployeeTests {

        @Test
        @DisplayName("С ролью DIRECTOR - успешное добавление, возвращает 201")
        void addEmployee_WithDirectorRole_ShouldReturnCreated() throws Exception {
            UUID orgId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            AddEmployeeRequest request = new AddEmployeeRequest(userId, "WORKER");

            EmployeeResponse response = EmployeeResponse.builder()
                    .userId(userId)
                    .orgId(orgId)
                    .username("Иванов Иван")
                    .email("ivanov@test.com")
                    .role("WORKER")
                    .joinedAt(LocalDateTime.now())
                    .build();

            when(employeeManagementService.addEmployee(eq(orgId), any(AddEmployeeRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/organizations/{orgId}/employees", orgId)
                            .header("X-User-Role", "DIRECTOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.userId").value(userId.toString()))
                    .andExpect(jsonPath("$.role").value("WORKER"));

            verify(employeeManagementService).addEmployee(eq(orgId), any(AddEmployeeRequest.class));
        }

        @Test
        @DisplayName("С ролью ACCOUNTANT - возвращает 403 Forbidden")
        void addEmployee_WithAccountantRole_ShouldReturnForbidden() throws Exception {
            UUID orgId = UUID.randomUUID();
            AddEmployeeRequest request = new AddEmployeeRequest(UUID.randomUUID(), "WORKER");

            mockMvc.perform(post("/api/organizations/{orgId}/employees", orgId)
                            .header("X-User-Role", "ACCOUNTANT")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(employeeManagementService, never()).addEmployee(any(), any());
        }

        @Test
        @DisplayName("С ролью WORKER - возвращает 403 Forbidden")
        void addEmployee_WithWorkerRole_ShouldReturnForbidden() throws Exception {
            UUID orgId = UUID.randomUUID();
            AddEmployeeRequest request = new AddEmployeeRequest(UUID.randomUUID(), "WORKER");

            mockMvc.perform(post("/api/organizations/{orgId}/employees", orgId)
                            .header("X-User-Role", "WORKER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Без роли - возвращает 403 Forbidden")
        void addEmployee_WithoutRole_ShouldReturnForbidden() throws Exception {
            UUID orgId = UUID.randomUUID();
            AddEmployeeRequest request = new AddEmployeeRequest(UUID.randomUUID(), "WORKER");

            mockMvc.perform(post("/api/organizations/{orgId}/employees", orgId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Без userId - возвращает 400")
        void addEmployee_WithoutUserId_ShouldReturnBadRequest() throws Exception {
            UUID orgId = UUID.randomUUID();
            String json = """
                {
                    "role": "WORKER"
                }
                """;

            mockMvc.perform(post("/api/organizations/{orgId}/employees", orgId)
                            .header("X-User-Role", "DIRECTOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Без роли сотрудника - возвращает 400")
        void addEmployee_WithoutEmployeeRole_ShouldReturnBadRequest() throws Exception {
            UUID orgId = UUID.randomUUID();
            String json = """
                {
                    "userId": "%s"
                }
                """.formatted(UUID.randomUUID());

            mockMvc.perform(post("/api/organizations/{orgId}/employees", orgId)
                            .header("X-User-Role", "DIRECTOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /api/organizations/{orgId}/employees/{userId} - Удаление сотрудника")
    class RemoveEmployeeTests {

        @Test
        @DisplayName("С ролью DIRECTOR - успешное удаление")
        void removeEmployee_WithDirectorRole_ShouldReturnOk() throws Exception {
            UUID orgId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            doNothing().when(employeeManagementService).removeEmployee(orgId, userId);

            mockMvc.perform(delete("/api/organizations/{orgId}/employees/{userId}", orgId, userId)
                            .header("X-User-Role", "DIRECTOR"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Сотрудник удален из организации"))
                    .andExpect(jsonPath("$.orgId").value(orgId.toString()))
                    .andExpect(jsonPath("$.userId").value(userId.toString()));

            verify(employeeManagementService).removeEmployee(orgId, userId);
        }

        @Test
        @DisplayName("С ролью ACCOUNTANT - возвращает 403")
        void removeEmployee_WithAccountantRole_ShouldReturnForbidden() throws Exception {
            UUID orgId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            mockMvc.perform(delete("/api/organizations/{orgId}/employees/{userId}", orgId, userId)
                            .header("X-User-Role", "ACCOUNTANT"))
                    .andExpect(status().isForbidden());

            verify(employeeManagementService, never()).removeEmployee(any(), any());
        }

        @Test
        @DisplayName("Без роли - возвращает 403")
        void removeEmployee_WithoutRole_ShouldReturnForbidden() throws Exception {
            UUID orgId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            mockMvc.perform(delete("/api/organizations/{orgId}/employees/{userId}", orgId, userId))
                    .andExpect(status().isForbidden());
        }
    }
}

