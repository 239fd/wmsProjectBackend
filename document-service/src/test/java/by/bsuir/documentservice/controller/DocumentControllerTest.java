package by.bsuir.documentservice.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
    private byte[] testDocumentBytes;
    private Map<String, Object> testMetadata;
    private Map<String, Object> testData;

    @BeforeEach
    void setUp() {
        testDocumentId = UUID.randomUUID();
        testDocumentBytes = "test document content".getBytes();

        testMetadata = new HashMap<>();
        testMetadata.put("type", "receipt-order");
        testMetadata.put("status", "generated");
        testMetadata.put("generatedAt", "2025-12-03T10:00:00");

        testData = new HashMap<>();
        testData.put("documentNumber", "TEST-001");
        testData.put("organizationName", "Test Organization");
    }

    @Test
    void getDocument_Success() {
        // Arrange
        when(documentService.getDocument(testDocumentId)).thenReturn(testDocumentBytes);

        // Act
        ResponseEntity<byte[]> response = documentController.getDocument(testDocumentId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(testDocumentBytes, response.getBody());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertTrue(
                response.getHeaders()
                        .getContentDisposition()
                        .toString()
                        .contains(testDocumentId.toString()));
        verify(documentService, times(1)).getDocument(testDocumentId);
    }

    @Test
    void getDocument_NotFound() {
        // Arrange
        when(documentService.getDocument(testDocumentId))
                .thenThrow(new RuntimeException("Document not found: " + testDocumentId));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> documentController.getDocument(testDocumentId));
        verify(documentService, times(1)).getDocument(testDocumentId);
    }

    @Test
    void getDocumentMetadata_Success() {
        // Arrange
        when(documentService.getDocumentMetadata(testDocumentId)).thenReturn(testMetadata);

        // Act
        ResponseEntity<Map<String, Object>> response =
                documentController.getDocumentMetadata(testDocumentId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(testMetadata, response.getBody());
        assertNotNull(response.getBody());
        assertEquals("receipt-order", response.getBody().get("type"));
        verify(documentService, times(1)).getDocumentMetadata(testDocumentId);
    }

    @Test
    void getDocumentMetadata_NotFound() {
        // Arrange
        when(documentService.getDocumentMetadata(testDocumentId))
                .thenThrow(new RuntimeException("Document metadata not found: " + testDocumentId));

        // Act & Assert
        assertThrows(
                RuntimeException.class,
                () -> documentController.getDocumentMetadata(testDocumentId));
        verify(documentService, times(1)).getDocumentMetadata(testDocumentId);
    }

    @Test
    void generateReceiptOrder_Success() {
        // Arrange
        when(documentService.generateReceiptOrder(any())).thenReturn(testDocumentId);

        // Act
        ResponseEntity<Map<String, String>> response =
                documentController.generateReceiptOrder(testData);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testDocumentId.toString(), response.getBody().get("documentId"));
        assertEquals("receipt-order", response.getBody().get("type"));
        assertEquals("stub", response.getBody().get("status"));
        verify(documentService, times(1)).generateReceiptOrder(testData);
    }

    @Test
    void generateShipmentOrder_Success() {
        // Arrange
        when(documentService.generateShipmentOrder(any())).thenReturn(testDocumentId);

        // Act
        ResponseEntity<Map<String, String>> response =
                documentController.generateShipmentOrder(testData);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testDocumentId.toString(), response.getBody().get("documentId"));
        assertEquals("shipment-order", response.getBody().get("type"));
        assertEquals("stub", response.getBody().get("status"));
        verify(documentService, times(1)).generateShipmentOrder(testData);
    }

    @Test
    void generateInventoryReport_Success() {
        // Arrange
        when(documentService.generateInventoryReport(any())).thenReturn(testDocumentId);

        // Act
        ResponseEntity<Map<String, String>> response =
                documentController.generateInventoryReport(testData);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testDocumentId.toString(), response.getBody().get("documentId"));
        assertEquals("inventory-report", response.getBody().get("type"));
        assertEquals("stub", response.getBody().get("status"));
        verify(documentService, times(1)).generateInventoryReport(testData);
    }

    @Test
    void generateRevaluationAct_Success() {
        // Arrange
        when(documentService.generateRevaluationAct(any())).thenReturn(testDocumentId);

        // Act
        ResponseEntity<Map<String, String>> response =
                documentController.generateRevaluationAct(testData);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testDocumentId.toString(), response.getBody().get("documentId"));
        assertEquals("revaluation-act", response.getBody().get("type"));
        assertEquals("stub", response.getBody().get("status"));
        verify(documentService, times(1)).generateRevaluationAct(testData);
    }

    @Test
    void generateWriteOffAct_Success() {
        // Arrange
        when(documentService.generateWriteOffAct(any())).thenReturn(testDocumentId);

        // Act
        ResponseEntity<Map<String, String>> response =
                documentController.generateWriteOffAct(testData);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testDocumentId.toString(), response.getBody().get("documentId"));
        assertEquals("write-off-act", response.getBody().get("type"));
        assertEquals("stub", response.getBody().get("status"));
        verify(documentService, times(1)).generateWriteOffAct(testData);
    }

    @Test
    void generateWaybill_Success() {
        // Arrange
        when(documentService.generateWaybill(any())).thenReturn(testDocumentId);

        // Act
        ResponseEntity<Map<String, String>> response = documentController.generateWaybill(testData);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testDocumentId.toString(), response.getBody().get("documentId"));
        assertEquals("waybill", response.getBody().get("type"));
        assertEquals("stub", response.getBody().get("status"));
        verify(documentService, times(1)).generateWaybill(testData);
    }

    @Test
    void generatePickingList_Success() {
        // Arrange
        when(documentService.generatePickingList(any())).thenReturn(testDocumentId);

        // Act
        ResponseEntity<Map<String, String>> response =
                documentController.generatePickingList(testData);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testDocumentId.toString(), response.getBody().get("documentId"));
        assertEquals("picking-list", response.getBody().get("type"));
        assertEquals("stub", response.getBody().get("status"));
        verify(documentService, times(1)).generatePickingList(testData);
    }

    @Test
    void getAllDocuments_Success() {
        // Arrange
        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("documents", List.of(testMetadata));
        mockResult.put("page", 0);
        mockResult.put("size", 20);
        mockResult.put("total", 1);

        when(documentService.getAllDocuments(0, 20)).thenReturn(mockResult);

        // Act
        ResponseEntity<Map<String, Object>> response = documentController.getAllDocuments(0, 20);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().get("page"));
        assertEquals(20, response.getBody().get("size"));
        assertEquals(1, response.getBody().get("total"));
        verify(documentService, times(1)).getAllDocuments(0, 20);
    }

    @Test
    void getAllDocuments_WithDefaultPagination() {
        // Arrange
        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("documents", new ArrayList<>());
        mockResult.put("page", 0);
        mockResult.put("size", 20);
        mockResult.put("total", 0);

        when(documentService.getAllDocuments(0, 20)).thenReturn(mockResult);

        // Act
        ResponseEntity<Map<String, Object>> response = documentController.getAllDocuments(0, 20);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(((List<?>) response.getBody().get("documents")).isEmpty());
        verify(documentService, times(1)).getAllDocuments(0, 20);
    }

    @Test
    void getStubInfo_Success() {
        // Act
        ResponseEntity<Map<String, Object>> response = documentController.getStubInfo();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Document Service", response.getBody().get("service"));
        assertEquals("STUB", response.getBody().get("status"));
        assertNotNull(response.getBody().get("version"));
        assertNotNull(response.getBody().get("message"));
    }

    @Test
    void generateReceiptOrder_WithEmptyData() {
        // Arrange
        Map<String, Object> emptyData = new HashMap<>();
        when(documentService.generateReceiptOrder(emptyData)).thenReturn(testDocumentId);

        // Act
        ResponseEntity<Map<String, String>> response =
                documentController.generateReceiptOrder(emptyData);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(documentService, times(1)).generateReceiptOrder(emptyData);
    }

    @Test
    void generateInventoryReport_WithNullData() {
        // Arrange
        when(documentService.generateInventoryReport(null)).thenReturn(testDocumentId);

        // Act
        ResponseEntity<Map<String, String>> response =
                documentController.generateInventoryReport(null);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(documentService, times(1)).generateInventoryReport(null);
    }
}
