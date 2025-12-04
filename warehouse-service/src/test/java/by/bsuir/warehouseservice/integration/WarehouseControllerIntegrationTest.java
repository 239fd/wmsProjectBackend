package by.bsuir.warehouseservice.integration;

import by.bsuir.warehouseservice.controller.WarehouseController;
import by.bsuir.warehouseservice.dto.request.CreateWarehouseRequest;
import by.bsuir.warehouseservice.dto.request.UpdateWarehouseRequest;
import by.bsuir.warehouseservice.dto.response.WarehouseResponse;
import by.bsuir.warehouseservice.service.WarehouseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
 * Интеграционные тесты для WarehouseController.
 *
 * Используем standalone MockMvc setup без поднятия Spring контекста.
 *
 * Тестируют веб-слой:
 * - HTTP маршрутизация
 * - Сериализация/десериализация JSON
 * - Проверка ролей (DIRECTOR, ACCOUNTANT)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WarehouseController Integration Tests")
class WarehouseControllerIntegrationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private WarehouseService warehouseService;

    @InjectMocks
    private WarehouseController warehouseController;

    private static final String BASE_URL = "/api/warehouses";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(warehouseController).build();
    }

    @Nested
    @DisplayName("POST /api/warehouses - Создание склада")
    class CreateWarehouseTests {

        @Test
        @DisplayName("С ролью DIRECTOR - успешное создание, возвращает 201")
        void createWarehouse_WithDirectorRole_ShouldReturnCreated() throws Exception {
            UUID orgId = UUID.randomUUID();
            CreateWarehouseRequest request = new CreateWarehouseRequest(
                    orgId, "Главный склад", "г. Минск, ул. Складская, 1", null
            );

            WarehouseResponse response = new WarehouseResponse(
                    UUID.randomUUID(), orgId, "Главный склад", "г. Минск, ул. Складская, 1",
                    null, true, LocalDateTime.now(), LocalDateTime.now()
            );

            when(warehouseService.createWarehouse(any(CreateWarehouseRequest.class))).thenReturn(response);

            mockMvc.perform(post(BASE_URL)
                            .header("X-User-Role", "DIRECTOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Главный склад"))
                    .andExpect(jsonPath("$.address").value("г. Минск, ул. Складская, 1"));

            verify(warehouseService).createWarehouse(any(CreateWarehouseRequest.class));
        }

        @Test
        @DisplayName("С ролью ACCOUNTANT - успешное создание")
        void createWarehouse_WithAccountantRole_ShouldReturnCreated() throws Exception {
            UUID orgId = UUID.randomUUID();
            CreateWarehouseRequest request = new CreateWarehouseRequest(
                    orgId, "Склад #2", "Адрес", null
            );

            WarehouseResponse response = new WarehouseResponse(
                    UUID.randomUUID(), orgId, "Склад #2", "Адрес", null, true,
                    LocalDateTime.now(), LocalDateTime.now()
            );

            when(warehouseService.createWarehouse(any())).thenReturn(response);

            mockMvc.perform(post(BASE_URL)
                            .header("X-User-Role", "ACCOUNTANT")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Без роли - возвращает 403 Forbidden")
        void createWarehouse_WithoutRole_ShouldReturnForbidden() throws Exception {
            CreateWarehouseRequest request = new CreateWarehouseRequest(
                    UUID.randomUUID(), "Склад", "Адрес", null
            );

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(warehouseService, never()).createWarehouse(any());
        }

        @Test
        @DisplayName("С ролью WORKER - возвращает 403 Forbidden")
        void createWarehouse_WithWorkerRole_ShouldReturnForbidden() throws Exception {
            CreateWarehouseRequest request = new CreateWarehouseRequest(
                    UUID.randomUUID(), "Склад", "Адрес", null
            );

            mockMvc.perform(post(BASE_URL)
                            .header("X-User-Role", "WORKER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/warehouses - Получение складов")
    class GetWarehousesTests {

        @Test
        @DisplayName("Получение склада по ID - возвращает 200")
        void getWarehouse_ById_ShouldReturnOk() throws Exception {
            UUID warehouseId = UUID.randomUUID();
            WarehouseResponse response = new WarehouseResponse(
                    warehouseId, UUID.randomUUID(), "Склад", "Адрес", null, true,
                    LocalDateTime.now(), LocalDateTime.now()
            );

            when(warehouseService.getWarehouse(warehouseId)).thenReturn(response);

            mockMvc.perform(get(BASE_URL + "/{warehouseId}", warehouseId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warehouseId").value(warehouseId.toString()))
                    .andExpect(jsonPath("$.name").value("Склад"));
        }

        @Test
        @DisplayName("Получение всех складов - возвращает список")
        void getAllWarehouses_ShouldReturnList() throws Exception {
            List<WarehouseResponse> warehouses = List.of(
                    new WarehouseResponse(UUID.randomUUID(), UUID.randomUUID(), "Склад 1", "Адрес 1", null, true, LocalDateTime.now(), LocalDateTime.now()),
                    new WarehouseResponse(UUID.randomUUID(), UUID.randomUUID(), "Склад 2", "Адрес 2", null, true, LocalDateTime.now(), LocalDateTime.now())
            );

            when(warehouseService.getAllWarehouses()).thenReturn(warehouses);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("Склад 1"))
                    .andExpect(jsonPath("$[1].name").value("Склад 2"));
        }

        @Test
        @DisplayName("Получение складов по организации - возвращает список")
        void getWarehousesByOrganization_ShouldReturnList() throws Exception {
            UUID orgId = UUID.randomUUID();
            List<WarehouseResponse> warehouses = List.of(
                    new WarehouseResponse(UUID.randomUUID(), orgId, "Склад 1", "Адрес 1", null, true, LocalDateTime.now(), LocalDateTime.now())
            );

            when(warehouseService.getWarehousesByOrganization(orgId)).thenReturn(warehouses);

            mockMvc.perform(get(BASE_URL + "/organization/{orgId}", orgId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("Получение активных складов организации - возвращает список")
        void getActiveWarehousesByOrganization_ShouldReturnList() throws Exception {
            UUID orgId = UUID.randomUUID();
            List<WarehouseResponse> warehouses = List.of(
                    new WarehouseResponse(UUID.randomUUID(), orgId, "Активный склад", "Адрес", null, true, LocalDateTime.now(), LocalDateTime.now())
            );

            when(warehouseService.getActiveWarehousesByOrganization(orgId)).thenReturn(warehouses);

            mockMvc.perform(get(BASE_URL + "/organization/{orgId}", orgId)
                            .param("activeOnly", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].isActive").value(true));
        }
    }

    @Nested
    @DisplayName("DELETE /api/warehouses - Удаление складов")
    class DeleteWarehouseTests {

        @Test
        @DisplayName("Удаление склада с ролью DIRECTOR - возвращает 200")
        void deleteWarehouse_WithDirectorRole_ShouldReturnOk() throws Exception {
            UUID warehouseId = UUID.randomUUID();
            doNothing().when(warehouseService).deleteWarehouse(warehouseId);

            mockMvc.perform(delete(BASE_URL + "/{warehouseId}", warehouseId)
                            .header("X-User-Role", "DIRECTOR"))
                    .andExpect(status().isOk());

            verify(warehouseService).deleteWarehouse(warehouseId);
        }

        @Test
        @DisplayName("Удаление склада без роли - возвращает 403")
        void deleteWarehouse_WithoutRole_ShouldReturnForbidden() throws Exception {
            UUID warehouseId = UUID.randomUUID();

            mockMvc.perform(delete(BASE_URL + "/{warehouseId}", warehouseId))
                    .andExpect(status().isForbidden());

            verify(warehouseService, never()).deleteWarehouse(any());
        }
    }
}
