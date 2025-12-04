package by.bsuir.documentservice.integration;

import by.bsuir.documentservice.controller.DocumentController;
import by.bsuir.documentservice.service.DocumentService;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты для DocumentController.
 *
 * Тестируют веб-слой:
 * - HTTP маршрутизация для генерации документов
 * - Получение документов и метаданных
 * - Сериализация/десериализация JSON
 */
@WebMvcTest(controllers = DocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@DisplayName("DocumentController Integration Tests")
class DocumentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DocumentService documentService;

    private static final String BASE_URL = "/api/documents";

    @Nested
    @DisplayName("GET /api/documents - Получение документов")
    class GetDocumentTests {

        @Test
        @DisplayName("Получение документа по ID - возвращает PDF")
        void getDocument_ById_ShouldReturnPdf() throws Exception {
            UUID documentId = UUID.randomUUID();
            byte[] pdfContent = "PDF content".getBytes();

            when(documentService.getDocument(documentId)).thenReturn(pdfContent);

            mockMvc.perform(get(BASE_URL + "/{documentId}", documentId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF));

            verify(documentService).getDocument(documentId);
        }

        @Test
        @DisplayName("Получение метаданных документа - возвращает JSON")
        void getDocumentMetadata_ShouldReturnJson() throws Exception {
            UUID documentId = UUID.randomUUID();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "receipt-order");
            metadata.put("createdAt", "2024-01-01T10:00:00");
            metadata.put("status", "generated");

            when(documentService.getDocumentMetadata(documentId)).thenReturn(metadata);

            mockMvc.perform(get(BASE_URL + "/{documentId}/metadata", documentId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("receipt-order"))
                    .andExpect(jsonPath("$.status").value("generated"));
        }
    }

    @Nested
    @DisplayName("POST /api/documents - Генерация документов")
    class GenerateDocumentTests {

        @Test
        @DisplayName("Генерация приходного ордера - возвращает 201")
        void generateReceiptOrder_ShouldReturnCreated() throws Exception {
            UUID documentId = UUID.randomUUID();
            Map<String, Object> data = new HashMap<>();
            data.put("warehouseId", UUID.randomUUID().toString());
            data.put("supplierId", UUID.randomUUID().toString());
            data.put("items", new Object[]{});

            when(documentService.generateReceiptOrder(any())).thenReturn(documentId);

            mockMvc.perform(post(BASE_URL + "/receipt-order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(data)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.documentId").value(documentId.toString()))
                    .andExpect(jsonPath("$.type").value("receipt-order"));

            verify(documentService).generateReceiptOrder(any());
        }

        @Test
        @DisplayName("Генерация расходного ордера - возвращает 201")
        void generateShipmentOrder_ShouldReturnCreated() throws Exception {
            UUID documentId = UUID.randomUUID();
            Map<String, Object> data = new HashMap<>();
            data.put("warehouseId", UUID.randomUUID().toString());
            data.put("customerId", UUID.randomUUID().toString());

            when(documentService.generateShipmentOrder(any())).thenReturn(documentId);

            mockMvc.perform(post(BASE_URL + "/shipment-order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(data)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.documentId").value(documentId.toString()))
                    .andExpect(jsonPath("$.type").value("shipment-order"));
        }

        @Test
        @DisplayName("Генерация инвентаризационной описи - возвращает 201")
        void generateInventoryReport_ShouldReturnCreated() throws Exception {
            UUID documentId = UUID.randomUUID();
            Map<String, Object> data = new HashMap<>();
            data.put("warehouseId", UUID.randomUUID().toString());
            data.put("date", "2024-01-01");

            when(documentService.generateInventoryReport(any())).thenReturn(documentId);

            mockMvc.perform(post(BASE_URL + "/inventory-report")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(data)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.documentId").value(documentId.toString()))
                    .andExpect(jsonPath("$.type").value("inventory-report"));
        }

        @Test
        @DisplayName("Генерация акта переоценки - возвращает 201")
        void generateRevaluationAct_ShouldReturnCreated() throws Exception {
            UUID documentId = UUID.randomUUID();
            Map<String, Object> data = new HashMap<>();
            data.put("warehouseId", UUID.randomUUID().toString());
            data.put("reason", "Плановая переоценка");

            when(documentService.generateRevaluationAct(any())).thenReturn(documentId);

            mockMvc.perform(post(BASE_URL + "/revaluation-act")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(data)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.documentId").exists())
                    .andExpect(jsonPath("$.type").value("revaluation-act"));
        }

        @Test
        @DisplayName("Генерация акта списания - возвращает 201")
        void generateWriteOffAct_ShouldReturnCreated() throws Exception {
            UUID documentId = UUID.randomUUID();
            Map<String, Object> data = new HashMap<>();
            data.put("warehouseId", UUID.randomUUID().toString());
            data.put("reason", "Истек срок годности");

            when(documentService.generateWriteOffAct(any())).thenReturn(documentId);

            mockMvc.perform(post(BASE_URL + "/write-off-act")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(data)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type").value("write-off-act"));
        }
    }

    @Nested
    @DisplayName("Тестирование JSON сериализации")
    class JsonSerializationTests {

        @Test
        @DisplayName("Ответ генерации содержит все поля")
        void generateDocument_ResponseShouldContainAllFields() throws Exception {
            UUID documentId = UUID.randomUUID();
            Map<String, Object> data = new HashMap<>();
            data.put("warehouseId", UUID.randomUUID().toString());

            when(documentService.generateReceiptOrder(any())).thenReturn(documentId);

            mockMvc.perform(post(BASE_URL + "/receipt-order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(data)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.documentId").exists())
                    .andExpect(jsonPath("$.type").exists())
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("Пустое тело запроса - обрабатывается корректно")
        void generateDocument_WithEmptyBody_ShouldProcess() throws Exception {
            UUID documentId = UUID.randomUUID();
            when(documentService.generateReceiptOrder(any())).thenReturn(documentId);

            mockMvc.perform(post(BASE_URL + "/receipt-order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isCreated());
        }
    }
}

