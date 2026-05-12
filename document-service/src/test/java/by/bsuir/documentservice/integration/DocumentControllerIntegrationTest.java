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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
    private static final String ORG_HEADER = "X-Organization-Id";
    private static final String USER_HEADER = "X-User-Id";

    @Nested
    @DisplayName("GET /api/documents - Получение документов")
    class GetDocumentTests {

        @Test
        @DisplayName("Получение документа по ID - возвращает PDF")
        void getDocument_ById_ShouldReturnPdf() throws Exception {
            UUID documentId = UUID.randomUUID();
            UUID orgId = UUID.randomUUID();
            byte[] pdfContent = "PDF content".getBytes();

            when(documentService.getDocument(eq(documentId), eq(orgId))).thenReturn(pdfContent);

            mockMvc.perform(get(BASE_URL + "/{documentId}", documentId)
                            .header(ORG_HEADER, orgId.toString()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF));

            verify(documentService).getDocument(eq(documentId), eq(orgId));
        }

        @Test
        @DisplayName("Получение метаданных документа - возвращает JSON")
        void getDocumentMetadata_ShouldReturnJson() throws Exception {
            UUID documentId = UUID.randomUUID();
            UUID orgId = UUID.randomUUID();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "receipt-order");
            metadata.put("format", "pdf");
            metadata.put("generatedAt", "2024-01-01T10:00:00");

            when(documentService.getDocumentMetadata(eq(documentId), eq(orgId))).thenReturn(metadata);

            mockMvc.perform(get(BASE_URL + "/{documentId}/metadata", documentId)
                            .header(ORG_HEADER, orgId.toString()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("receipt-order"))
                    .andExpect(jsonPath("$.format").value("pdf"));
        }
    }

    @Nested
    @DisplayName("POST /api/documents - Генерация документов")
    class GenerateDocumentTests {

        @Test
        @DisplayName("Генерация приходного ордера - возвращает 201")
        void generateReceiptOrder_ShouldReturnCreated() throws Exception {
            UUID documentId = UUID.randomUUID();
            UUID orgId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Map<String, Object> data = new HashMap<>();
            data.put("warehouseId", UUID.randomUUID().toString());
            data.put("supplierId", UUID.randomUUID().toString());
            data.put("items", new Object[]{});

            when(documentService.generateReceiptOrder(any(), any(), any(), any())).thenReturn(documentId);

            mockMvc.perform(post(BASE_URL + "/receipt-order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(ORG_HEADER, orgId.toString())
                            .header(USER_HEADER, userId.toString())
                            .content(objectMapper.writeValueAsString(data)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.documentId").value(documentId.toString()))
                    .andExpect(jsonPath("$.type").value("receipt-order"));

            verify(documentService).generateReceiptOrder(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Генерация расходного ордера - возвращает 201, тип release-order")
        void generateShipmentOrder_ShouldReturnCreated() throws Exception {
            UUID documentId = UUID.randomUUID();
            Map<String, Object> data = new HashMap<>();
            data.put("warehouseId", UUID.randomUUID().toString());
            data.put("customerId", UUID.randomUUID().toString());

            when(documentService.generateShipmentOrder(any(), any(), any(), any())).thenReturn(documentId);

            mockMvc.perform(post(BASE_URL + "/shipment-order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(data)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.documentId").value(documentId.toString()))
                    .andExpect(jsonPath("$.type").value("release-order"));
        }

        @Test
        @DisplayName("Генерация инвентаризационной описи - возвращает 201")
        void generateInventoryReport_ShouldReturnCreated() throws Exception {
            UUID documentId = UUID.randomUUID();
            Map<String, Object> data = new HashMap<>();
            data.put("warehouseId", UUID.randomUUID().toString());
            data.put("date", "2024-01-01");

            when(documentService.generateInventoryReport(any(), any(), any(), any())).thenReturn(documentId);

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

            when(documentService.generateRevaluationAct(any(), any(), any(), any())).thenReturn(documentId);

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

            when(documentService.generateWriteOffAct(any(), any(), any(), any())).thenReturn(documentId);

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

            when(documentService.generateReceiptOrder(any(), any(), any(), any())).thenReturn(documentId);

            mockMvc.perform(post(BASE_URL + "/receipt-order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(data)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.documentId").exists())
                    .andExpect(jsonPath("$.type").exists())
                    .andExpect(jsonPath("$.status").value("generated"));
        }

        @Test
        @DisplayName("Пустое тело запроса - обрабатывается корректно")
        void generateDocument_WithEmptyBody_ShouldProcess() throws Exception {
            UUID documentId = UUID.randomUUID();
            when(documentService.generateReceiptOrder(any(), any(), any(), any())).thenReturn(documentId);

            mockMvc.perform(post(BASE_URL + "/receipt-order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isCreated());
        }
    }
}
