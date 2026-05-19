package by.bsuir.documentservice.controller;

import by.bsuir.documentservice.service.DocumentService;
import by.bsuir.documentservice.service.DocumentService.GenerationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentController Tests")
class DocumentControllerTest {

    @Mock
    private DocumentService documentService;

    @InjectMocks
    private DocumentController controller;

    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUpServiceStub() {
        org.mockito.Mockito.lenient().when(documentService.generate(any(), any(), any(), any(), any()))
                .thenReturn(new GenerationResult(new byte[]{9, 9}, "programmatic", "pdf"));
    }

    @Test
    @DisplayName("generateReceiptOrder: 200 OK + APPLICATION_PDF + X-Generation-Channel header")
    void generateReceiptOrder_whenCalled_thenReturnsPdf() {
        ResponseEntity<byte[]> response = controller.generateReceiptOrder(
                Map.of("k", "v"), "pdf", "auto", orgId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst("X-Generation-Channel")).isEqualTo("programmatic");
        verify(documentService).generate(eq("receipt-order"), any(), eq(orgId), eq("pdf"), eq("auto"));
    }

    @Test
    @DisplayName("generateInventoryReport: делегирует с правильным type")
    void generateInventoryReport_whenCalled_thenDelegates() {
        controller.generateInventoryReport(Map.of(), "pdf", "auto", orgId);
        verify(documentService).generate(eq("inventory-report"), any(), eq(orgId), eq("pdf"), eq("auto"));
    }

    @Test
    @DisplayName("generateRevaluationAct: делегирует с правильным type")
    void generateRevaluationAct_whenCalled_thenDelegates() {
        controller.generateRevaluationAct(Map.of(), "pdf", "auto", orgId);
        verify(documentService).generate(eq("revaluation-act"), any(), eq(orgId), eq("pdf"), eq("auto"));
    }

    @Test
    @DisplayName("generateWriteOffAct: делегирует с правильным type")
    void generateWriteOffAct_whenCalled_thenDelegates() {
        controller.generateWriteOffAct(Map.of(), "pdf", "auto", orgId);
        verify(documentService).generate(eq("write-off-act"), any(), eq(orgId), eq("pdf"), eq("auto"));
    }

    @Test
    @DisplayName("generateWaybill: делегирует с правильным type")
    void generateWaybill_whenCalled_thenDelegates() {
        controller.generateWaybill(Map.of(), "pdf", "auto", orgId);
        verify(documentService).generate(eq("waybill"), any(), eq(orgId), eq("pdf"), eq("auto"));
    }

    @Test
    @DisplayName("generatePickingList: делегирует с правильным type")
    void generatePickingList_whenCalled_thenDelegates() {
        controller.generatePickingList(Map.of(), "pdf", "auto", orgId);
        verify(documentService).generate(eq("picking-list"), any(), eq(orgId), eq("pdf"), eq("auto"));
    }

    @Test
    @DisplayName("generatePlacementList: делегирует с правильным type")
    void generatePlacementList_whenCalled_thenDelegates() {
        controller.generatePlacementList(Map.of(), "pdf", "auto", orgId);
        verify(documentService).generate(eq("placement-list"), any(), eq(orgId), eq("pdf"), eq("auto"));
    }

    @Test
    @DisplayName("generateReceiptAct: делегирует с правильным type")
    void generateReceiptAct_whenCalled_thenDelegates() {
        controller.generateReceiptAct(Map.of(), "pdf", "auto", orgId);
        verify(documentService).generate(eq("receipt-act"), any(), eq(orgId), eq("pdf"), eq("auto"));
    }

    @Test
    @DisplayName("generateInvoice: делегирует с правильным type")
    void generateInvoice_whenCalled_thenDelegates() {
        controller.generateInvoice(Map.of(), "pdf", "auto", orgId);
        verify(documentService).generate(eq("invoice"), any(), eq(orgId), eq("pdf"), eq("auto"));
    }

    @Test
    @DisplayName("generateTransportNote: вкладывает layout в payload")
    void generateTransportNote_whenCalled_thenPutsLayoutIntoPayload() {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        controller.generateTransportNote(data, "pdf", "vertical", "auto", orgId);

        assertThat(data).containsEntry("layout", "vertical");
        verify(documentService).generate(eq("transport-note"), any(), eq(orgId), eq("pdf"), eq("auto"));
    }

    @Test
    @DisplayName("generateCmr: делегирует с правильным type")
    void generateCmr_whenCalled_thenDelegates() {
        controller.generateCmr(Map.of(), "pdf", "auto", orgId);
        verify(documentService).generate(eq("cmr"), any(), eq(orgId), eq("pdf"), eq("auto"));
    }

    @Test
    @DisplayName("generateInvoice(mode=rpa): прокидывает mode в DocumentService")
    void generateInvoice_givenRpaMode_whenCalled_thenPassesMode() {
        when(documentService.generate(any(), any(), any(), any(), eq("rpa")))
                .thenReturn(new GenerationResult(new byte[]{1, 2, 3}, "rpa", "docx"));

        ResponseEntity<byte[]> response = controller.generateInvoice(Map.of(), "pdf", "rpa", orgId);

        assertThat(response.getHeaders().getFirst("X-Generation-Channel")).isEqualTo("rpa");
        verify(documentService).generate(eq("invoice"), any(), eq(orgId), eq("pdf"), eq("rpa"));
    }

    @Test
    @DisplayName("getStubInfo: возвращает info Map с 10 типами документов")
    void getStubInfo_whenCalled_thenReturnsServiceInfo() {
        ResponseEntity<Map<String, Object>> response = controller.getStubInfo();

        assertThat(response.getBody()).containsKeys("service", "status", "version", "documentTypes");
        String[] types = (String[]) response.getBody().get("documentTypes");
        assertThat(types).hasSize(10);
        assertThat(types).contains("receipt-order", "cmr", "invoice", "picking-list");
    }
}
