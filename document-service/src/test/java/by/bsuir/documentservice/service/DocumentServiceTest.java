package by.bsuir.documentservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import by.bsuir.documentservice.dto.*;
import by.bsuir.documentservice.rpa.DocumentRpaService;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock private DocumentRpaService rpaService;

    @InjectMocks private DocumentService documentService;

    private Map<String, Object> testData;
    private byte[] testDocumentBytes;

    @BeforeEach
    void setUp() {
        testDocumentBytes = "test document content".getBytes();

        testData = new HashMap<>();
        testData.put("documentNumber", "TEST-001");
        testData.put("documentDate", "2025-12-03");
        testData.put("organizationName", "Test Organization");
        testData.put("inn", "1234567890");
        testData.put("warehouseName", "Test Warehouse");
    }

    @Test
    void generateReceiptOrder_Success() {
        // Arrange
        when(rpaService.generateReceiptOrder(any(ReceiptOrderData.class)))
                .thenReturn(testDocumentBytes);

        // Act
        UUID documentId = documentService.generateReceiptOrder(testData);

        // Assert
        assertNotNull(documentId);
        verify(rpaService, times(1)).generateReceiptOrder(any(ReceiptOrderData.class));

        // Verify document is stored
        byte[] storedDocument = documentService.getDocument(documentId);
        assertArrayEquals(testDocumentBytes, storedDocument);

        // Verify metadata
        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId);
        assertEquals("receipt-order", metadata.get("type"));
        assertEquals("generated", metadata.get("status"));
    }

    @Test
    void generateReceiptOrder_RpaError_CreatesStub() {
        // Arrange
        when(rpaService.generateReceiptOrder(any(ReceiptOrderData.class)))
                .thenThrow(new RuntimeException("RPA Error"));

        // Act
        UUID documentId = documentService.generateReceiptOrder(testData);

        // Assert
        assertNotNull(documentId);
        verify(rpaService, times(1)).generateReceiptOrder(any(ReceiptOrderData.class));

        // Verify stub metadata is created
        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId);
        assertEquals("receipt-order", metadata.get("type"));
        assertEquals("stub", metadata.get("status"));
    }

    @Test
    void generateRevaluationAct_Success() {
        // Arrange
        testData.put("reason", "INFLATION");
        testData.put("reasonDescription", "Test revaluation");
        testData.put("chairmanName", "Test Chairman");
        testData.put("commissionMembers", Arrays.asList("Member1", "Member2"));

        when(rpaService.generateRevaluationAct(any(RevaluationActData.class)))
                .thenReturn(testDocumentBytes);

        // Act
        UUID documentId = documentService.generateRevaluationAct(testData);

        // Assert
        assertNotNull(documentId);
        verify(rpaService, times(1)).generateRevaluationAct(any(RevaluationActData.class));

        byte[] storedDocument = documentService.getDocument(documentId);
        assertArrayEquals(testDocumentBytes, storedDocument);

        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId);
        assertEquals("revaluation-act", metadata.get("type"));
        assertEquals("generated", metadata.get("status"));
    }

    @Test
    void generateRevaluationAct_RpaError_CreatesStub() {
        // Arrange
        when(rpaService.generateRevaluationAct(any(RevaluationActData.class)))
                .thenThrow(new RuntimeException("RPA Error"));

        // Act
        UUID documentId = documentService.generateRevaluationAct(testData);

        // Assert
        assertNotNull(documentId);
        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId);
        assertEquals("revaluation-act", metadata.get("type"));
        assertEquals("stub", metadata.get("status"));
    }

    @Test
    void generateInventoryReport_Success() {
        // Arrange
        testData.put("inventoryDate", "2025-12-03");
        testData.put("chairmanName", "Test Chairman");
        testData.put("commissionMembers", List.of("Member1"));
        testData.put("responsiblePerson", "Test Person");

        when(rpaService.generateInventoryList(any(InventoryListData.class)))
                .thenReturn(testDocumentBytes);

        // Act
        UUID documentId = documentService.generateInventoryReport(testData);

        // Assert
        assertNotNull(documentId);
        verify(rpaService, times(1)).generateInventoryList(any(InventoryListData.class));

        byte[] storedDocument = documentService.getDocument(documentId);
        assertArrayEquals(testDocumentBytes, storedDocument);

        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId);
        assertEquals("inventory-report", metadata.get("type"));
        assertEquals("generated", metadata.get("status"));
    }

    @Test
    void generateInventoryReport_RpaError_CreatesStub() {
        // Arrange
        when(rpaService.generateInventoryList(any(InventoryListData.class)))
                .thenThrow(new RuntimeException("RPA Error"));

        // Act
        UUID documentId = documentService.generateInventoryReport(testData);

        // Assert
        assertNotNull(documentId);
        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId);
        assertEquals("inventory-report", metadata.get("type"));
        assertEquals("stub", metadata.get("status"));
    }

    @Test
    void generateWriteOffAct_Success() {
        // Arrange
        testData.put("reason", "DAMAGE");

        when(rpaService.generateWriteOffAct(any(WriteOffActData.class)))
                .thenReturn(testDocumentBytes);

        // Act
        UUID documentId = documentService.generateWriteOffAct(testData);

        // Assert
        assertNotNull(documentId);
        verify(rpaService, times(1)).generateWriteOffAct(any(WriteOffActData.class));

        byte[] storedDocument = documentService.getDocument(documentId);
        assertArrayEquals(testDocumentBytes, storedDocument);

        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId);
        assertEquals("write-off-act", metadata.get("type"));
        assertEquals("generated", metadata.get("status"));
        assertEquals("docx", metadata.get("format"));
    }

    @Test
    void generateWriteOffAct_RpaError_CreatesStub() {
        // Arrange
        when(rpaService.generateWriteOffAct(any(WriteOffActData.class)))
                .thenThrow(new RuntimeException("RPA Error"));

        // Act
        UUID documentId = documentService.generateWriteOffAct(testData);

        // Assert
        assertNotNull(documentId);
        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId);
        assertEquals("write-off-act", metadata.get("type"));
        assertEquals("stub", metadata.get("status"));
    }

    @Test
    void generateShipmentOrder_CreatesStub() {
        // Act
        UUID documentId = documentService.generateShipmentOrder(testData);

        // Assert
        assertNotNull(documentId);
        verifyNoInteractions(rpaService);

        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId);
        assertEquals("shipment-order", metadata.get("type"));
        assertEquals("stub", metadata.get("status"));
    }

    @Test
    void generateWaybill_Success() {
        // Arrange
        testData.put("invoiceNumber", "TTN-001");
        testData.put("invoiceDate", "2025-12-03");
        testData.put("shipperName", "Shipper");
        testData.put("consigneeName", "Consignee");
        testData.put("carrierName", "Carrier");

        when(rpaService.generateShippingInvoice(any(ShippingInvoiceData.class)))
                .thenReturn(testDocumentBytes);

        // Act
        UUID documentId = documentService.generateWaybill(testData);

        // Assert
        assertNotNull(documentId);
        verify(rpaService, times(1)).generateShippingInvoice(any(ShippingInvoiceData.class));

        byte[] storedDocument = documentService.getDocument(documentId);
        assertArrayEquals(testDocumentBytes, storedDocument);

        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId);
        assertEquals("shipping-invoice", metadata.get("type"));
        assertEquals("generated", metadata.get("status"));
    }

    @Test
    void generateWaybill_RpaError_CreatesStub() {
        // Arrange
        when(rpaService.generateShippingInvoice(any(ShippingInvoiceData.class)))
                .thenThrow(new RuntimeException("RPA Error"));

        // Act
        UUID documentId = documentService.generateWaybill(testData);

        // Assert
        assertNotNull(documentId);
        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId);
        assertEquals("shipping-invoice", metadata.get("type"));
        assertEquals("stub", metadata.get("status"));
    }

    @Test
    void generatePickingList_CreatesStub() {
        // Act
        UUID documentId = documentService.generatePickingList(testData);

        // Assert
        assertNotNull(documentId);
        verifyNoInteractions(rpaService);

        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId);
        assertEquals("picking-list", metadata.get("type"));
        assertEquals("stub", metadata.get("status"));
    }

    @Test
    void getDocument_Success() {
        // Arrange
        when(rpaService.generateReceiptOrder(any(ReceiptOrderData.class)))
                .thenReturn(testDocumentBytes);
        UUID documentId = documentService.generateReceiptOrder(testData);

        // Act
        byte[] result = documentService.getDocument(documentId);

        // Assert
        assertNotNull(result);
        assertArrayEquals(testDocumentBytes, result);
    }

    @Test
    void getDocument_NotFound_ThrowsException() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();

        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class, () -> documentService.getDocument(nonExistentId));
        assertTrue(exception.getMessage().contains("Document not found"));
    }

    @Test
    void getDocumentMetadata_Success() {
        // Arrange
        when(rpaService.generateReceiptOrder(any(ReceiptOrderData.class)))
                .thenReturn(testDocumentBytes);
        UUID documentId = documentService.generateReceiptOrder(testData);

        // Act
        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId);

        // Assert
        assertNotNull(metadata);
        assertEquals("receipt-order", metadata.get("type"));
        assertEquals("generated", metadata.get("status"));
        assertNotNull(metadata.get("generatedAt"));
        assertNotNull(metadata.get("format"));
    }

    @Test
    void getDocumentMetadata_NotFound_ThrowsException() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();

        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentService.getDocumentMetadata(nonExistentId));
        assertTrue(exception.getMessage().contains("Document metadata not found"));
    }

    @Test
    void getAllDocuments_EmptyList() {
        // Act
        Map<String, Object> result = documentService.getAllDocuments(0, 20);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.get("page"));
        assertEquals(20, result.get("size"));
        assertEquals(0, result.get("total"));
        assertTrue(((List<?>) result.get("documents")).isEmpty());
    }

    @Test
    void getAllDocuments_WithDocuments() {
        // Arrange
        when(rpaService.generateReceiptOrder(any(ReceiptOrderData.class)))
                .thenReturn(testDocumentBytes);
        documentService.generateReceiptOrder(testData);
        documentService.generateShipmentOrder(testData);

        // Act
        Map<String, Object> result = documentService.getAllDocuments(0, 20);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.get("page"));
        assertEquals(20, result.get("size"));
        assertEquals(2, result.get("total"));
        assertEquals(2, ((List<?>) result.get("documents")).size());
    }

    @Test
    void getAllDocuments_WithPagination() {
        // Arrange
        when(rpaService.generateReceiptOrder(any(ReceiptOrderData.class)))
                .thenReturn(testDocumentBytes);
        for (int i = 0; i < 5; i++) {
            documentService.generateReceiptOrder(testData);
        }

        // Act
        Map<String, Object> result = documentService.getAllDocuments(1, 2);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.get("page"));
        assertEquals(2, result.get("size"));
        assertEquals(5, result.get("total"));
        assertEquals(2, ((List<?>) result.get("documents")).size());
    }

    @Test
    void getAllDocuments_PageOutOfBounds() {
        // Arrange
        when(rpaService.generateReceiptOrder(any(ReceiptOrderData.class)))
                .thenReturn(testDocumentBytes);
        documentService.generateReceiptOrder(testData);

        // Act
        Map<String, Object> result = documentService.getAllDocuments(10, 20);

        // Assert
        assertNotNull(result);
        assertEquals(10, result.get("page"));
        assertEquals(20, result.get("size"));
        assertEquals(1, result.get("total"));
        assertTrue(((List<?>) result.get("documents")).isEmpty());
    }

    @Test
    void generateReceiptOrder_WithEmptyData() {
        // Arrange
        when(rpaService.generateReceiptOrder(any(ReceiptOrderData.class)))
                .thenReturn(testDocumentBytes);
        Map<String, Object> emptyData = new HashMap<>();

        // Act
        UUID documentId = documentService.generateReceiptOrder(emptyData);

        // Assert
        assertNotNull(documentId);
        verify(rpaService, times(1)).generateReceiptOrder(any(ReceiptOrderData.class));
    }

    @Test
    void generateRevaluationAct_WithDefaultValues() {
        // Arrange
        when(rpaService.generateRevaluationAct(any(RevaluationActData.class)))
                .thenReturn(testDocumentBytes);
        Map<String, Object> minimalData = new HashMap<>();

        // Act
        UUID documentId = documentService.generateRevaluationAct(minimalData);

        // Assert
        assertNotNull(documentId);
        verify(rpaService, times(1)).generateRevaluationAct(any(RevaluationActData.class));
    }

    @Test
    void generateInventoryReport_WithDefaultValues() {
        // Arrange
        when(rpaService.generateInventoryList(any(InventoryListData.class)))
                .thenReturn(testDocumentBytes);
        Map<String, Object> minimalData = new HashMap<>();

        // Act
        UUID documentId = documentService.generateInventoryReport(minimalData);

        // Assert
        assertNotNull(documentId);
        verify(rpaService, times(1)).generateInventoryList(any(InventoryListData.class));
    }

    @Test
    void generateWriteOffAct_WithDefaultValues() {
        // Arrange
        when(rpaService.generateWriteOffAct(any(WriteOffActData.class)))
                .thenReturn(testDocumentBytes);
        Map<String, Object> minimalData = new HashMap<>();

        // Act
        UUID documentId = documentService.generateWriteOffAct(minimalData);

        // Assert
        assertNotNull(documentId);
        verify(rpaService, times(1)).generateWriteOffAct(any(WriteOffActData.class));
    }

    @Test
    void multipleDocumentsGeneration_Success() {
        // Arrange
        when(rpaService.generateReceiptOrder(any(ReceiptOrderData.class)))
                .thenReturn(testDocumentBytes);
        when(rpaService.generateRevaluationAct(any(RevaluationActData.class)))
                .thenReturn(testDocumentBytes);
        when(rpaService.generateInventoryList(any(InventoryListData.class)))
                .thenReturn(testDocumentBytes);

        // Act
        UUID doc1 = documentService.generateReceiptOrder(testData);
        UUID doc2 = documentService.generateRevaluationAct(testData);
        UUID doc3 = documentService.generateInventoryReport(testData);

        // Assert
        assertNotNull(doc1);
        assertNotNull(doc2);
        assertNotNull(doc3);
        assertNotEquals(doc1, doc2);
        assertNotEquals(doc2, doc3);

        Map<String, Object> allDocs = documentService.getAllDocuments(0, 10);
        assertEquals(3, allDocs.get("total"));
    }
}
