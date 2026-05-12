package by.bsuir.documentservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import by.bsuir.documentservice.dto.ReceiptOrderData;
import by.bsuir.documentservice.dto.RevaluationActData;
import by.bsuir.documentservice.rpa.DocumentRpaService;
import by.bsuir.documentservice.rpa.PdfDocumentService;
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
    @Mock private PdfDocumentService pdfService;
    @Mock private DataEnrichmentService enrichmentService;

    @InjectMocks private DocumentService documentService;

    private UUID orgId;
    private UUID userId;
    private Map<String, Object> testData;
    private byte[] testDocumentBytes;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
        testDocumentBytes = "test document content".getBytes();

        testData = new HashMap<>();
        testData.put("documentNumber", "TEST-001");
        testData.put("documentDate", "2025-12-03");
        testData.put("organizationName", "Test Organization");
        testData.put("inn", "1234567890");
        testData.put("warehouseName", "Test Warehouse");

        lenient().when(enrichmentService.enrich(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void generateReceiptOrder_StoresMetadata() {
        UUID documentId = documentService.generateReceiptOrder(testData, orgId, userId, "pdf");

        assertNotNull(documentId);

        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId, orgId);
        assertEquals(documentId.toString(), metadata.get("id"));
        assertEquals("receipt-order", metadata.get("type"));
        assertEquals("pdf", metadata.get("format"));
        assertEquals(orgId, metadata.get("organizationId"));
        assertEquals(userId, metadata.get("generatedBy"));
        assertNotNull(metadata.get("generatedAt"));
    }

    @Test
    void getDocument_PdfFormat_DelegatesToPdfService() {
        when(pdfService.generateReceiptOrderPdf(any())).thenReturn(testDocumentBytes);

        UUID documentId = documentService.generateReceiptOrder(testData, orgId, userId, "pdf");
        byte[] result = documentService.getDocument(documentId, orgId);

        assertArrayEquals(testDocumentBytes, result);
        verify(pdfService, times(1)).generateReceiptOrderPdf(any());
        verifyNoInteractions(rpaService);
    }

    @Test
    void getDocument_RpaXlsFormat_DelegatesToRpaService() {
        when(rpaService.generateReceiptOrder(any(ReceiptOrderData.class))).thenReturn(testDocumentBytes);

        UUID documentId = documentService.generateReceiptOrder(testData, orgId, userId, "rpa-xls");
        byte[] result = documentService.getDocument(documentId, orgId);

        assertArrayEquals(testDocumentBytes, result);
        verify(rpaService, times(1)).generateReceiptOrder(any(ReceiptOrderData.class));
        verifyNoInteractions(pdfService);
    }

    @Test
    void getDocument_DefaultFormatIsPdf_WhenNullFormat() {
        when(pdfService.generateReceiptOrderPdf(any())).thenReturn(testDocumentBytes);

        UUID documentId = documentService.generateReceiptOrder(testData, orgId, userId, null);
        byte[] result = documentService.getDocument(documentId, orgId);

        assertArrayEquals(testDocumentBytes, result);

        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId, orgId);
        assertEquals("pdf", metadata.get("format"));
    }

    @Test
    void generateRevaluationAct_StoresMetadata() {
        testData.put("reason", "INFLATION");
        testData.put("reasonDescription", "Test revaluation");
        testData.put("chairmanName", "Test Chairman");
        testData.put("commissionMembers", Arrays.asList("Member1", "Member2"));

        UUID documentId = documentService.generateRevaluationAct(testData, orgId, userId, "pdf");

        assertNotNull(documentId);
        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId, orgId);
        assertEquals("revaluation-act", metadata.get("type"));
    }

    @Test
    void getDocument_RevaluationAct_RpaXls_UsesRpaService() {
        when(rpaService.generateRevaluationAct(any(RevaluationActData.class))).thenReturn(testDocumentBytes);

        UUID documentId = documentService.generateRevaluationAct(testData, orgId, userId, "rpa-xls");
        byte[] result = documentService.getDocument(documentId, orgId);

        assertArrayEquals(testDocumentBytes, result);
        verify(rpaService, times(1)).generateRevaluationAct(any(RevaluationActData.class));
    }

    @Test
    void generateInventoryReport_StoresMetadata() {
        UUID documentId = documentService.generateInventoryReport(testData, orgId, userId, "pdf");

        assertNotNull(documentId);
        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId, orgId);
        assertEquals("inventory-report", metadata.get("type"));
    }

    @Test
    void generateWriteOffAct_StoresMetadata() {
        testData.put("reason", "DAMAGE");

        UUID documentId = documentService.generateWriteOffAct(testData, orgId, userId, "pdf");

        assertNotNull(documentId);
        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId, orgId);
        assertEquals("write-off-act", metadata.get("type"));
        assertEquals("pdf", metadata.get("format"));
    }

    @Test
    void generateShipmentOrder_UsesReleaseOrderType() {
        UUID documentId = documentService.generateShipmentOrder(testData, orgId, userId, "pdf");

        assertNotNull(documentId);
        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId, orgId);
        assertEquals("release-order", metadata.get("type"));
    }

    @Test
    void generateWaybill_StoresMetadata() {
        UUID documentId = documentService.generateWaybill(testData, orgId, userId, "pdf");

        assertNotNull(documentId);
        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId, orgId);
        assertEquals("waybill", metadata.get("type"));
    }

    @Test
    void generatePickingList_StoresMetadata() {
        UUID documentId = documentService.generatePickingList(testData, orgId, userId, "pdf");

        assertNotNull(documentId);
        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId, orgId);
        assertEquals("picking-list", metadata.get("type"));
    }

    @Test
    void getDocument_NotFound_ThrowsException() {
        UUID nonExistentId = UUID.randomUUID();

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentService.getDocument(nonExistentId, orgId));
        assertTrue(exception.getMessage().contains("Document not found"));
    }

    @Test
    void getDocument_DifferentOrganization_ThrowsException() {
        UUID documentId = documentService.generateReceiptOrder(testData, orgId, userId, "pdf");

        UUID otherOrg = UUID.randomUUID();

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentService.getDocument(documentId, otherOrg));
        assertTrue(exception.getMessage().contains("another organization"));
    }

    @Test
    void getDocumentMetadata_NotFound_ThrowsException() {
        UUID nonExistentId = UUID.randomUUID();

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentService.getDocumentMetadata(nonExistentId, orgId));
        assertTrue(exception.getMessage().contains("Document not found"));
    }

    @Test
    void getAllDocuments_EmptyList() {
        Map<String, Object> result = documentService.getAllDocuments(0, 20, orgId);

        assertNotNull(result);
        assertEquals(0, result.get("page"));
        assertEquals(20, result.get("size"));
        assertEquals(0, result.get("total"));
        assertTrue(((List<?>) result.get("documents")).isEmpty());
    }

    @Test
    void getAllDocuments_WithDocuments() {
        documentService.generateReceiptOrder(testData, orgId, userId, "pdf");
        documentService.generateShipmentOrder(testData, orgId, userId, "pdf");

        Map<String, Object> result = documentService.getAllDocuments(0, 20, orgId);

        assertNotNull(result);
        assertEquals(0, result.get("page"));
        assertEquals(20, result.get("size"));
        assertEquals(2, result.get("total"));
        assertEquals(2, ((List<?>) result.get("documents")).size());
    }

    @Test
    void getAllDocuments_FiltersByOrganization() {
        UUID otherOrg = UUID.randomUUID();
        documentService.generateReceiptOrder(testData, orgId, userId, "pdf");
        documentService.generateReceiptOrder(testData, orgId, userId, "pdf");
        documentService.generateReceiptOrder(testData, otherOrg, userId, "pdf");

        Map<String, Object> result = documentService.getAllDocuments(0, 20, orgId);

        assertEquals(2, result.get("total"));
        assertEquals(2, ((List<?>) result.get("documents")).size());
    }

    @Test
    void getAllDocuments_WithPagination() {
        for (int i = 0; i < 5; i++) {
            documentService.generateReceiptOrder(testData, orgId, userId, "pdf");
        }

        Map<String, Object> result = documentService.getAllDocuments(1, 2, orgId);

        assertNotNull(result);
        assertEquals(1, result.get("page"));
        assertEquals(2, result.get("size"));
        assertEquals(5, result.get("total"));
        assertEquals(2, ((List<?>) result.get("documents")).size());
    }

    @Test
    void getAllDocuments_PageOutOfBounds() {
        documentService.generateReceiptOrder(testData, orgId, userId, "pdf");

        Map<String, Object> result = documentService.getAllDocuments(10, 20, orgId);

        assertNotNull(result);
        assertEquals(10, result.get("page"));
        assertEquals(20, result.get("size"));
        assertEquals(1, result.get("total"));
        assertTrue(((List<?>) result.get("documents")).isEmpty());
    }

    @Test
    void generateReceiptOrder_WithEmptyData() {
        Map<String, Object> emptyData = new HashMap<>();

        UUID documentId = documentService.generateReceiptOrder(emptyData, orgId, userId, "pdf");

        assertNotNull(documentId);
        verify(enrichmentService).enrich(eq(emptyData), eq(orgId));
    }

    @Test
    void multipleDocumentsGeneration_Success() {
        UUID doc1 = documentService.generateReceiptOrder(testData, orgId, userId, "pdf");
        UUID doc2 = documentService.generateRevaluationAct(testData, orgId, userId, "pdf");
        UUID doc3 = documentService.generateInventoryReport(testData, orgId, userId, "pdf");

        assertNotNull(doc1);
        assertNotNull(doc2);
        assertNotNull(doc3);
        assertNotEquals(doc1, doc2);
        assertNotEquals(doc2, doc3);

        Map<String, Object> allDocs = documentService.getAllDocuments(0, 10, orgId);
        assertEquals(3, allDocs.get("total"));
    }
}
