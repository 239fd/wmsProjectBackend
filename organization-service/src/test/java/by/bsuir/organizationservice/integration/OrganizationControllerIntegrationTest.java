package by.bsuir.organizationservice.integration;

import by.bsuir.organizationservice.controller.OrganizationController;
import by.bsuir.organizationservice.dto.request.CreateOrganizationRequest;
import by.bsuir.organizationservice.dto.request.UpdateOrganizationRequest;
import by.bsuir.organizationservice.dto.response.OrganizationResponse;
import by.bsuir.organizationservice.model.enums.OrganizationStatus;
import by.bsuir.organizationservice.service.OrganizationService;
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
 * Интеграционные тесты для OrganizationController.
 *
 * Тестируют веб-слой:
 * - HTTP маршрутизация
 * - Валидация запросов
 * - Проверка ролей (только DIRECTOR может создавать)
 * - Сериализация/десериализация JSON
 */
@WebMvcTest(controllers = OrganizationController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@DisplayName("OrganizationController Integration Tests")
class OrganizationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrganizationService organizationService;

    private static final String BASE_URL = "/api/organizations";

    @Nested
    @DisplayName("POST /api/organizations - Создание организации")
    class CreateOrganizationTests {

        @Test
        @DisplayName("С ролью DIRECTOR - успешное создание, возвращает 201")
        void createOrganization_WithDirectorRole_ShouldReturnCreated() throws Exception {
            UUID userId = UUID.randomUUID();
            CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                    .name("ООО Тестовая компания")
                    .shortName("Тест")
                    .unp("123456789")
                    .address("г. Минск, ул. Тестовая, 1")
                    .build();

            OrganizationResponse response = new OrganizationResponse(
                    UUID.randomUUID(),
                    "ООО Тестовая компания",
                    "Тест",
                    "123456789",
                    "г. Минск, ул. Тестовая, 1",
                    OrganizationStatus.ACTIVE,
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );

            when(organizationService.createOrganization(any(CreateOrganizationRequest.class), eq(userId)))
                    .thenReturn(response);

            mockMvc.perform(post(BASE_URL)
                            .header("X-User-Id", userId.toString())
                            .header("X-User-Role", "DIRECTOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("ООО Тестовая компания"))
                    .andExpect(jsonPath("$.unp").value("123456789"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));

            verify(organizationService).createOrganization(any(CreateOrganizationRequest.class), eq(userId));
        }

        @Test
        @DisplayName("С ролью ACCOUNTANT - возвращает 403 Forbidden")
        void createOrganization_WithAccountantRole_ShouldReturnForbidden() throws Exception {
            CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                    .name("ООО Компания")
                    .unp("123456789")
                    .build();

            mockMvc.perform(post(BASE_URL)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "ACCOUNTANT")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(organizationService, never()).createOrganization(any(), any());
        }

        @Test
        @DisplayName("С ролью WORKER - возвращает 403 Forbidden")
        void createOrganization_WithWorkerRole_ShouldReturnForbidden() throws Exception {
            CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                    .name("ООО Компания")
                    .unp("123456789")
                    .build();

            mockMvc.perform(post(BASE_URL)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "WORKER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Пустое название - возвращает 400")
        void createOrganization_WithEmptyName_ShouldReturnBadRequest() throws Exception {
            CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                    .name("")
                    .unp("123456789")
                    .build();

            mockMvc.perform(post(BASE_URL)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "DIRECTOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Пустой УНП - возвращает 400")
        void createOrganization_WithEmptyUnp_ShouldReturnBadRequest() throws Exception {
            CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                    .name("ООО Компания")
                    .unp("")
                    .build();

            mockMvc.perform(post(BASE_URL)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "DIRECTOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/organizations - Получение организаций")
    class GetOrganizationsTests {

        @Test
        @DisplayName("Получение организации по ID - возвращает 200")
        void getOrganization_ById_ShouldReturnOk() throws Exception {
            UUID orgId = UUID.randomUUID();
            OrganizationResponse response = new OrganizationResponse(
                    orgId, "ООО Компания", "Компания", "123456789", "Адрес",
                    OrganizationStatus.ACTIVE,
                    LocalDateTime.now(), LocalDateTime.now()
            );

            when(organizationService.getOrganization(orgId)).thenReturn(response);

            mockMvc.perform(get(BASE_URL + "/{orgId}", orgId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orgId").value(orgId.toString()))
                    .andExpect(jsonPath("$.name").value("ООО Компания"));
        }

        @Test
        @DisplayName("Получение всех организаций - возвращает список")
        void getAllOrganizations_ShouldReturnList() throws Exception {
            List<OrganizationResponse> organizations = List.of(
                    new OrganizationResponse(UUID.randomUUID(), "Компания 1", "К1", "111", "Адрес 1", OrganizationStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now()),
                    new OrganizationResponse(UUID.randomUUID(), "Компания 2", "К2", "222", "Адрес 2", OrganizationStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now())
            );

            when(organizationService.getAllOrganizations()).thenReturn(organizations);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("Компания 1"))
                    .andExpect(jsonPath("$[1].name").value("Компания 2"));
        }

        @Test
        @DisplayName("Фильтрация по статусу ACTIVE - возвращает только активные")
        void getOrganizations_FilterByActiveStatus_ShouldReturnActive() throws Exception {
            List<OrganizationResponse> organizations = List.of(
                    new OrganizationResponse(UUID.randomUUID(), "Активная компания", "АК", "111", "Адрес", OrganizationStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now())
            );

            when(organizationService.getOrganizationsByStatus(OrganizationStatus.ACTIVE)).thenReturn(organizations);

            mockMvc.perform(get(BASE_URL)
                            .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].status").value("ACTIVE"));
        }

        @Test
        @DisplayName("Фильтрация по статусу BLOCKED - возвращает заблокированные")
        void getOrganizations_FilterByBlockedStatus_ShouldReturnBlocked() throws Exception {
            List<OrganizationResponse> organizations = List.of(
                    new OrganizationResponse(UUID.randomUUID(), "Заблокированная", "З", "222", "Адрес", OrganizationStatus.BLOCKED, LocalDateTime.now(), LocalDateTime.now())
            );

            when(organizationService.getOrganizationsByStatus(OrganizationStatus.BLOCKED)).thenReturn(organizations);

            mockMvc.perform(get(BASE_URL)
                            .param("status", "BLOCKED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("BLOCKED"));
        }
    }

    @Nested
    @DisplayName("Тестирование валидации")
    class ValidationTests {

        @Test
        @DisplayName("Слишком длинное название - возвращает 400")
        void createOrganization_WithTooLongName_ShouldReturnBadRequest() throws Exception {
            String longName = "A".repeat(300);
            CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                    .name(longName)
                    .unp("123456789")
                    .build();

            mockMvc.perform(post(BASE_URL)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "DIRECTOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Слишком длинный УНП - возвращает 400")
        void createOrganization_WithTooLongUnp_ShouldReturnBadRequest() throws Exception {
            CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                    .name("Компания")
                    .unp("A".repeat(25))
                    .build();

            mockMvc.perform(post(BASE_URL)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "DIRECTOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }
}

