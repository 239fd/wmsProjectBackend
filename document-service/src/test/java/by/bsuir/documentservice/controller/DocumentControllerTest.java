package by.bsuir.documentservice.controller;

import by.bsuir.documentservice.config.RpaProperties;
import by.bsuir.documentservice.dto.OfficeFillRequest;
import by.bsuir.documentservice.rpa.OfficeDocumentBot;
import by.bsuir.documentservice.service.DocumentService;
import by.bsuir.documentservice.service.DocumentService.GenerationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
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

    @Mock
    private ObjectProvider<OfficeDocumentBot> officeBotProvider;

    @Mock
    private RpaProperties rpaProperties;

    @InjectMocks
    private DocumentController controller;

    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUpServiceStub() {
        // each generate-*** delegates to documentService.generate; use lenient default for all types
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
    @DisplayName("generateInventoryReport: 200 OK")
    void generateInventoryReport_whenCalled_thenDelegates() {
        controller.generateInventoryReport(Map.of(), "pdf", "auto", orgId);
        verify(documentService).generate(eq("inventory-report"), any(), eq(orgId), eq("pdf"), eq("auto"));
    }

    @Test
    @DisplayName("generateRevaluationAct: 200 OK")
    void generateRevaluationAct_whenCalled_thenDelegates() {
        controller.generateRevaluationAct(Map.of(), "pdf", "auto", orgId);
        verify(documentService).generate(eq("revaluation-act"), any(), eq(orgId), eq("pdf"), eq("auto"));
    }

    @Test
    @DisplayName("generateWriteOffAct: 200 OK")
    void generateWriteOffAct_whenCalled_thenDelegates() {
        controller.generateWriteOffAct(Map.of(), "pdf", "auto", orgId);
        verify(documentService).generate(eq("write-off-act"), any(), eq(orgId), eq("pdf"), eq("auto"));
    }

    @Test
    @DisplayName("generateWaybill: 200 OK")
    void generateWaybill_whenCalled_thenDelegates() {
        controller.generateWaybill(Map.of(), "pdf", "auto", orgId);
        verify(documentService).generate(eq("waybill"), any(), eq(orgId), eq("pdf"), eq("auto"));
    }

    @Test
    @DisplayName("generatePickingList: 200 OK")
    void generatePickingList_whenCalled_thenDelegates() {
        controller.generatePickingList(Map.of(), "pdf", "auto", orgId);
        verify(documentService).generate(eq("picking-list"), any(), eq(orgId), eq("pdf"), eq("auto"));
    }

    @Test
    @DisplayName("generatePlacementList: 200 OK")
    void generatePlacementList_whenCalled_thenDelegates() {
        controller.generatePlacementList(Map.of(), "pdf", "auto", orgId);
        verify(documentService).generate(eq("placement-list"), any(), eq(orgId), eq("pdf"), eq("auto"));
    }

    @Test
    @DisplayName("generateReceiptAct: 200 OK")
    void generateReceiptAct_whenCalled_thenDelegates() {
        controller.generateReceiptAct(Map.of(), "pdf", "auto", orgId);
        verify(documentService).generate(eq("receipt-act"), any(), eq(orgId), eq("pdf"), eq("auto"));
    }

    @Test
    @DisplayName("generateInvoice: 200 OK")
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
    @DisplayName("generateCmr: 200 OK")
    void generateCmr_whenCalled_thenDelegates() {
        controller.generateCmr(Map.of(), "pdf", "auto", orgId);
        verify(documentService).generate(eq("cmr"), any(), eq(orgId), eq("pdf"), eq("auto"));
    }

    @Test
    @DisplayName("officeHealth: bot available → enabled=true")
    void officeHealth_givenBotAvailable_whenCalled_thenReturnsEnabledTrue() {
        when(officeBotProvider.getIfAvailable()).thenReturn(org.mockito.Mockito.mock(OfficeDocumentBot.class));

        ResponseEntity<Map<String, Object>> response = controller.officeHealth();

        assertThat(response.getBody()).containsEntry("enabled", true);
    }

    @Test
    @DisplayName("officeHealth: bot not available → enabled=false с reason")
    void officeHealth_givenBotUnavailable_whenCalled_thenReturnsEnabledFalse() {
        when(officeBotProvider.getIfAvailable()).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = controller.officeHealth();

        assertThat(response.getBody()).containsEntry("enabled", false);
        assertThat(response.getBody().get("reason").toString()).contains("not wired");
    }

    @Test
    @DisplayName("fillOfficeTemplate: bot disabled → 503 SERVICE_UNAVAILABLE")
    void fillOfficeTemplate_givenBotDisabled_whenCalled_thenReturns503() {
        OfficeFillRequest req = new OfficeFillRequest("any.xlsx", null, Map.of(), Map.of());
        when(officeBotProvider.getIfAvailable()).thenReturn(null);

        ResponseEntity<byte[]> response = controller.fillOfficeTemplate(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("fillOfficeTemplate: bot available но template не существует → 404 NOT_FOUND")
    void fillOfficeTemplate_givenMissingTemplate_whenCalled_thenReturns404() {
        OfficeFillRequest req = new OfficeFillRequest("nonexistent.xlsx", null, Map.of(), Map.of());
        OfficeDocumentBot bot = org.mockito.Mockito.mock(OfficeDocumentBot.class);
        when(officeBotProvider.getIfAvailable()).thenReturn(bot);
        RpaProperties.Templates templates = new RpaProperties.Templates();
        templates.setDir("nonexistent-dir/");
        when(rpaProperties.getTemplates()).thenReturn(templates);

        ResponseEntity<byte[]> response = controller.fillOfficeTemplate(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("fillOfficeTemplate: bot available + xlsx template → Excel-вариант, 200 OK")
    void fillOfficeTemplate_givenExcelTemplate_whenCalled_thenUsesExcelBranch(@TempDir Path tmp) throws Exception {
        Path template = tmp.resolve("t.xlsx");
        Files.writeString(template, "x");
        Path output = tmp.resolve("out.xlsx");
        Files.writeString(output, "result");
        OfficeFillRequest req = new OfficeFillRequest("t.xlsx", null, Map.of("A1", "v"), null);
        OfficeDocumentBot bot = org.mockito.Mockito.mock(OfficeDocumentBot.class);
        when(officeBotProvider.getIfAvailable()).thenReturn(bot);
        RpaProperties.Templates templates = new RpaProperties.Templates();
        templates.setDir(tmp.toString() + java.io.File.separator);
        when(rpaProperties.getTemplates()).thenReturn(templates);
        when(bot.fillExcelTemplate(any(), any(), any())).thenReturn(output);

        ResponseEntity<byte[]> response = controller.fillOfficeTemplate(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Generation-Channel")).isEqualTo("rpa");
    }

    @Test
    @DisplayName("fillOfficeTemplate: bot available + docx template → Word-вариант, 200 OK")
    void fillOfficeTemplate_givenWordTemplate_whenCalled_thenUsesWordBranch(@TempDir Path tmp) throws Exception {
        Path template = tmp.resolve("t.docx");
        Files.writeString(template, "x");
        Path output = tmp.resolve("out.docx");
        Files.writeString(output, "result");
        OfficeFillRequest req = new OfficeFillRequest("t.docx", "custom-name", null, Map.of("k", "v"));
        OfficeDocumentBot bot = org.mockito.Mockito.mock(OfficeDocumentBot.class);
        when(officeBotProvider.getIfAvailable()).thenReturn(bot);
        RpaProperties.Templates templates = new RpaProperties.Templates();
        templates.setDir(tmp.toString() + java.io.File.separator);
        when(rpaProperties.getTemplates()).thenReturn(templates);
        when(bot.fillWordTemplate(any(), any(), any())).thenReturn(output);

        ResponseEntity<byte[]> response = controller.fillOfficeTemplate(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("fillOfficeTemplate: bot бросает исключение → 500 INTERNAL_SERVER_ERROR")
    void fillOfficeTemplate_givenBotThrows_whenCalled_thenReturns500(@TempDir Path tmp) throws Exception {
        Path template = tmp.resolve("t.xlsx");
        Files.writeString(template, "x");
        OfficeFillRequest req = new OfficeFillRequest("t.xlsx", null, Map.of(), null);
        OfficeDocumentBot bot = org.mockito.Mockito.mock(OfficeDocumentBot.class);
        when(officeBotProvider.getIfAvailable()).thenReturn(bot);
        RpaProperties.Templates templates = new RpaProperties.Templates();
        templates.setDir(tmp.toString() + java.io.File.separator);
        when(rpaProperties.getTemplates()).thenReturn(templates);
        when(bot.fillExcelTemplate(any(), any(), any())).thenThrow(new RuntimeException("bot crash"));

        ResponseEntity<byte[]> response = controller.fillOfficeTemplate(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
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
