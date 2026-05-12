package by.bsuir.documentservice.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import by.bsuir.documentservice.service.DocumentService;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    @Mock private DocumentService documentService;

    @InjectMocks private DocumentController documentController;

    private UUID testDocumentId;
    private UUID testOrgId;
    private UUID testUserId;
    private byte[] testDocumentBytes;
    private Map<String, Object> testMetadata;
    private Map<String, Object> testData;

    @BeforeEach
    void setUp() {
        testDocumentId = UUID.randomUUID();
        testOrgId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
        testDocumentBytes = "test document content".getBytes();

        testMetadata = new HashMap<>();
        testMetadata.put("type", "receipt-order");
        testMetadata.put("format", "pdf");
        testMetadata.put("generatedAt", "2025-12-03T10:00:00");

        testData = new HashMap<>();
        testData.put("documentNumber", "TEST-001");
        testData.put("organizationName", "Test Organization");
    }

    @Test
    void getDocument_Success() {
        when(documentService.getDocument(testDocumentId, testOrgId)).thenReturn(testDocumentBytes);

        ResponseEntity<byte[]> response = documentController.getDocument(testDocumentId, testOrgId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(testDocumentBytes, response.getBody());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertTrue(
                response.getHeaders()
                        .getContentDisposition()
                        .toString()
                        .contains(testDocumentId.toString()));
        verify(documentService, times(1)).getDocument(testDocumentId, testOrgId);
    }

    @Test
    void getDocument_NotFound() {
        when(documentService.getDocument(testDocumentId, testOrgId))
                .thenThrow(new RuntimeException("Document not found: " + testDocumentId));

        assertThrows(
                RuntimeException.class,
                () -> documentController.getDocument(testDocumentId, testOrgId));
        verify(documentService, times(1)).getDocument(testDocumentId, testOrgId);
    }

    @Test
    void getDocumentMetadata_Success() {
        when(documentService.getDocumentMetadata(testDocumentId, testOrgId))
                .thenReturn(testMetadata);

        ResponseEntity<Map<String, Object>> response =
                documentController.getDocumentMetadata(testDocumentId, testOrgId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(testMetadata, response.getBody());
        assertNotNull(response.getBody());
        assertEquals("receipt-order", response.getBody().get("type"));
        verify(documentService, times(1)).getDocumentMetadata(testDocumentId, testOrgId);
    }

    @Test
    void getDocumentMetadata_NotFound() {
        when(documentService.getDocumentMetadata(testDocumentId, testOrgId))
                .thenThrow(new RuntimeException("Document metadata not found: " + testDocumentId));

        assertThrows(
                RuntimeException.class,
                () -> documentController.getDocumentMetadata(testDocumentId, testOrgId));
        verify(documentService, times(1)).getDocumentMetadata(testDocumentId, testOrgId);
    }

    @Test
    void generateReceiptOrder_Success() {
        when(documentService.generateReceiptOrder(eq(testData), eq(testOrgId), eq(testUserId), eq("pdf")))
                .thenReturn(testDocumentId);

        ResponseEntity<Map<String, String>> response =
                documentController.generateReceiptOrder(testData, "pdf", testOrgId, testUserId);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testDocumentId.toString(), response.getBody().get("documentId"));
        assertEquals("receipt-order", response.getBody().get("type"));
        assertEquals("generated", response.getBody().get("status"));
        verify(documentService, times(1)).generateReceiptOrder(testData, testOrgId, testUserId, "pdf");
    }

    @Test
    void generateShipmentOrder_Success() {
        when(documentService.generateShipmentOrder(eq(testData), eq(testOrgId), eq(testUserId), eq("pdf")))
                .thenReturn(testDocumentId);

        ResponseEntity<Map<String, String>> response =
                documentController.generateShipmentOrder(testData, "pdf", testOrgId, testUserId);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testDocumentId.toString(), response.getBody().get("documentId"));

        assertEquals("release-order", response.getBody().get("type"));
        assertEquals("generated", response.getBody().get("status"));
        verify(documentService, times(1)).generateShipmentOrder(testData, testOrgId, testUserId, "pdf");
    }

    @Test
    void generateInventoryReport_Success() {
        when(documentService.generateInventoryReport(eq(testData), eq(testOrgId), eq(testUserId), eq("pdf")))
                .thenReturn(testDocumentId);

        ResponseEntity<Map<String, String>> response =
                documentController.generateInventoryReport(testData, "pdf", testOrgId, testUserId);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testDocumentId.toString(), response.getBody().get("documentId"));
        assertEquals("inventory-report", response.getBody().get("type"));
        assertEquals("generated", response.getBody().get("status"));
        verify(documentService, times(1)).generateInventoryReport(testData, testOrgId, testUserId, "pdf");
    }

    @Test
    void generateRevaluationAct_Success() {
        when(documentService.generateRevaluationAct(eq(testData), eq(testOrgId), eq(testUserId), eq("pdf")))
                .thenReturn(testDocumentId);

        ResponseEntity<Map<String, String>> response =
                documentController.generateRevaluationAct(testData, "pdf", testOrgId, testUserId);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testDocumentId.toString(), response.getBody().get("documentId"));
        assertEquals("revaluation-act", response.getBody().get("type"));
        assertEquals("generated", response.getBody().get("status"));
        verify(documentService, times(1)).generateRevaluationAct(testData, testOrgId, testUserId, "pdf");
    }

    @Test
    void generateWriteOffAct_Success() {
        when(documentService.generateWriteOffAct(eq(testData), eq(testOrgId), eq(testUserId), eq("pdf")))
                .thenReturn(testDocumentId);

        ResponseEntity<Map<String, String>> response =
                documentController.generateWriteOffAct(testData, "pdf", testOrgId, testUserId);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testDocumentId.toString(), response.getBody().get("documentId"));
        assertEquals("write-off-act", response.getBody().get("type"));
        assertEquals("generated", response.getBody().get("status"));
        verify(documentService, times(1)).generateWriteOffAct(testData, testOrgId, testUserId, "pdf");
    }

    @Test
    void generateWaybill_Success() {
        when(documentService.generateWaybill(eq(testData), eq(testOrgId), eq(testUserId), eq("pdf")))
                .thenReturn(testDocumentId);

        ResponseEntity<Map<String, String>> response =
                documentController.generateWaybill(testData, "pdf", testOrgId, testUserId);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testDocumentId.toString(), response.getBody().get("documentId"));
        assertEquals("waybill", response.getBody().get("type"));
        assertEquals("generated", response.getBody().get("status"));
        verify(documentService, times(1)).generateWaybill(testData, testOrgId, testUserId, "pdf");
    }

    @Test
    void generatePickingList_Success() {
        when(documentService.generatePickingList(eq(testData), eq(testOrgId), eq(testUserId), eq("pdf")))
                .thenReturn(testDocumentId);

        ResponseEntity<Map<String, String>> response =
                documentController.generatePickingList(testData, "pdf", testOrgId, testUserId);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testDocumentId.toString(), response.getBody().get("documentId"));
        assertEquals("picking-list", response.getBody().get("type"));
        assertEquals("generated", response.getBody().get("status"));
        verify(documentService, times(1)).generatePickingList(testData, testOrgId, testUserId, "pdf");
    }

    @Test
    void getAllDocuments_Success() {
        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("documents", List.of(testMetadata));
        mockResult.put("page", 0);
        mockResult.put("size", 20);
        mockResult.put("total", 1);

        when(documentService.getAllDocuments(0, 20, testOrgId)).thenReturn(mockResult);

        ResponseEntity<Map<String, Object>> response =
                documentController.getAllDocuments(0, 20, testOrgId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().get("page"));
        assertEquals(20, response.getBody().get("size"));
        assertEquals(1, response.getBody().get("total"));
        verify(documentService, times(1)).getAllDocuments(0, 20, testOrgId);
    }

    @Test
    void getAllDocuments_WithDefaultPagination() {
        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("documents", new ArrayList<>());
        mockResult.put("page", 0);
        mockResult.put("size", 20);
        mockResult.put("total", 0);

        when(documentService.getAllDocuments(0, 20, testOrgId)).thenReturn(mockResult);

        ResponseEntity<Map<String, Object>> response =
                documentController.getAllDocuments(0, 20, testOrgId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(((List<?>) response.getBody().get("documents")).isEmpty());
        verify(documentService, times(1)).getAllDocuments(0, 20, testOrgId);
    }

    @Test
    void getStubInfo_Success() {
        ResponseEntity<Map<String, Object>> response = documentController.getStubInfo();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Document Service", response.getBody().get("service"));
        assertEquals("active", response.getBody().get("status"));
        assertNotNull(response.getBody().get("version"));
        assertNotNull(response.getBody().get("documentTypes"));
        assertNotNull(response.getBody().get("formats"));
    }

    @Test
    void generateReceiptOrder_WithEmptyData() {
        Map<String, Object> emptyData = new HashMap<>();
        when(documentService.generateReceiptOrder(eq(emptyData), eq(testOrgId), eq(testUserId), eq("pdf")))
                .thenReturn(testDocumentId);

        ResponseEntity<Map<String, String>> response =
                documentController.generateReceiptOrder(emptyData, "pdf", testOrgId, testUserId);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(documentService, times(1)).generateReceiptOrder(emptyData, testOrgId, testUserId, "pdf");
    }

    @Test
    void generateInventoryReport_WithNullData() {
        when(documentService.generateInventoryReport(eq(null), eq(testOrgId), eq(testUserId), eq("pdf")))
                .thenReturn(testDocumentId);

        ResponseEntity<Map<String, String>> response =
                documentController.generateInventoryReport(null, "pdf", testOrgId, testUserId);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(documentService, times(1)).generateInventoryReport(null, testOrgId, testUserId, "pdf");
    }
}
