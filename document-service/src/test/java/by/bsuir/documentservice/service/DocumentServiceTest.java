package by.bsuir.documentservice.service;

import by.bsuir.documentservice.dto.*;
import by.bsuir.documentservice.rpa.DocumentRpaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentService Tests")
class DocumentServiceTest {

    @Mock
    private DocumentRpaService rpaService;

    @InjectMocks
    private DocumentService documentService;

    private Map<String, Object> testData;

    @BeforeEach
    void setUp() {
        testData = new HashMap<>();
        testData.put("organizationName", "Test Organization");
        testData.put("warehouseName", "Test Warehouse");
        testData.put("documentNumber", "DOC-001");
        testData.put("date", LocalDate.now().toString());
        testData.put("responsible", "John Doe");
    }

    @Test
    @DisplayName("Should generate receipt order successfully")
    void shouldGenerateReceiptOrderSuccessfully() {
        byte[] mockDocument = "PDF content".getBytes();
        when(rpaService.generateReceiptOrder(any(ReceiptOrderData.class)))
                .thenReturn(mockDocument);

        UUID documentId = documentService.generateReceiptOrder(testData);

        assertThat(documentId).isNotNull();
        verify(rpaService, times(1)).generateReceiptOrder(any(ReceiptOrderData.class));

        byte[] document = documentService.getDocument(documentId);
        assertThat(document).isEqualTo(mockDocument);
    }

    @Test
    @DisplayName("Should handle receipt order generation failure gracefully")
    void shouldHandleReceiptOrderGenerationFailure() {
        when(rpaService.generateReceiptOrder(any(ReceiptOrderData.class)))
                .thenThrow(new RuntimeException("RPA service error"));

        UUID documentId = documentService.generateReceiptOrder(testData);

        assertThat(documentId).isNotNull();
        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId);
        assertThat(metadata.get("status")).isEqualTo("stub");
    }

    @Test
    @DisplayName("Should generate revaluation act successfully")
    void shouldGenerateRevaluationActSuccessfully() {
        byte[] mockDocument = "PDF content".getBytes();
        when(rpaService.generateRevaluationAct(any(RevaluationActData.class)))
                .thenReturn(mockDocument);

        testData.put("oldPrice", "100.00");
        testData.put("newPrice", "120.00");

        UUID documentId = documentService.generateRevaluationAct(testData);

        assertThat(documentId).isNotNull();
        verify(rpaService, times(1)).generateRevaluationAct(any(RevaluationActData.class));
    }

    @Test
    @DisplayName("Should generate inventory report successfully")
    void shouldGenerateInventoryReportSuccessfully() {
        byte[] mockDocument = "PDF content".getBytes();
        when(rpaService.generateInventoryList(any(InventoryListData.class)))
                .thenReturn(mockDocument);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("productName", "Test Product");
        item.put("quantity", "10");
        items.add(item);
        testData.put("items", items);

        UUID documentId = documentService.generateInventoryReport(testData);

        assertThat(documentId).isNotNull();
        verify(rpaService, times(1)).generateInventoryList(any(InventoryListData.class));
    }

    @Test
    @DisplayName("Should generate write-off act successfully")
    void shouldGenerateWriteOffActSuccessfully() {
        byte[] mockDocument = "PDF content".getBytes();
        when(rpaService.generateWriteOffAct(any(WriteOffActData.class)))
                .thenReturn(mockDocument);

        testData.put("reason", "Damaged");

        UUID documentId = documentService.generateWriteOffAct(testData);

        assertThat(documentId).isNotNull();
        verify(rpaService, times(1)).generateWriteOffAct(any(WriteOffActData.class));
    }

    @Test
    @DisplayName("Should generate shipment order successfully")
    void shouldGenerateShipmentOrderSuccessfully() {
        byte[] mockDocument = "PDF content".getBytes();
        when(rpaService.generateShippingInvoice(any(ShippingInvoiceData.class)))
                .thenReturn(mockDocument);

        UUID documentId = documentService.generateShipmentOrder(testData);

        assertThat(documentId).isNotNull();
        verify(rpaService, times(1)).generateShippingInvoice(any(ShippingInvoiceData.class));
    }

    @Test
    @DisplayName("Should generate waybill successfully")
    void shouldGenerateWaybillSuccessfully() {
        UUID documentId = documentService.generateWaybill(testData);

        assertThat(documentId).isNotNull();
        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId);
        assertThat(metadata).isNotNull();
        assertThat(metadata.get("type")).isEqualTo("waybill");
    }

    @Test
    @DisplayName("Should generate picking list successfully")
    void shouldGeneratePickingListSuccessfully() {
        UUID documentId = documentService.generatePickingList(testData);

        assertThat(documentId).isNotNull();
        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId);
        assertThat(metadata).isNotNull();
        assertThat(metadata.get("type")).isEqualTo("picking-list");
    }

    @Test
    @DisplayName("Should retrieve all documents with pagination")
    void shouldRetrieveAllDocumentsWithPagination() {
        documentService.generateWaybill(testData);
        documentService.generatePickingList(testData);

        Map<String, Object> result = documentService.getAllDocuments(0, 10);

        assertThat(result).isNotNull();
        assertThat(result).containsKeys("documents", "totalElements", "currentPage", "pageSize");
        List<?> documents = (List<?>) result.get("documents");
        assertThat(documents).hasSize(2);
    }

    @Test
    @DisplayName("Should retrieve document metadata")
    void shouldRetrieveDocumentMetadata() {
        UUID documentId = documentService.generateWaybill(testData);

        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId);

        assertThat(metadata).isNotNull();
        assertThat(metadata.get("type")).isEqualTo("waybill");
        assertThat(metadata.get("status")).isEqualTo("stub");
        assertThat(metadata).containsKey("createdAt");
    }
}

