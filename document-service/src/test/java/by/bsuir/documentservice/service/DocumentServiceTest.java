package by.bsuir.documentservice.service;

import by.bsuir.documentservice.rpa.DocumentRpaService;
import by.bsuir.documentservice.rpa.PdfDocumentService;
import by.bsuir.documentservice.rpa.PythonRpaClient;
import by.bsuir.documentservice.rpa.PythonRpaClient.FillResponse;
import by.bsuir.documentservice.service.DocumentService.GenerationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentService Tests")
class DocumentServiceTest {

    @Mock
    private DocumentRpaService rpaService;

    @Mock
    private PdfDocumentService pdfService;

    @Mock
    private DataEnrichmentService enrichmentService;

    @Mock
    private PythonRpaClient pythonRpaClient;

    @InjectMocks
    private DocumentService service;

    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(enrichmentService.enrich(any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("generate(auto, pdf): использует PDF-канал и channel=programmatic")
    void generate_givenAutoModePdf_whenCalled_thenUsesPdfChannel() {
        when(pdfService.generateReceiptOrderPdf(any())).thenReturn(new byte[]{1, 2});

        GenerationResult result = service.generate("receipt-order", Map.of(), orgId, "pdf", "auto");

        assertThat(result.body()).containsExactly(1, 2);
        assertThat(result.channel()).isEqualTo("programmatic");
        assertThat(result.format()).isEqualTo("pdf");
    }

    @Test
    @DisplayName("generate(legacy 4-arg): возвращает только bytes, дефолт mode=auto")
    void generate_givenLegacyOverload_whenCalled_thenReturnsBytesOnly() {
        when(pdfService.generateReceiptOrderPdf(any())).thenReturn(new byte[]{7});

        byte[] body = service.generate("receipt-order", Map.of(), orgId, "pdf");

        assertThat(body).containsExactly(7);
    }

    @Test
    @DisplayName("generate(mode=rpa): Python отвечает → channel=rpa, body из Python")
    void generate_givenRpaModeSuccess_whenCalled_thenChannelIsRpa() {
        byte[] pythonBytes = new byte[]{0x50, 0x4B, 0x03, 0x04};
        when(pythonRpaClient.fill(eq("invoice"), any()))
                .thenReturn(new FillResponse(pythonBytes,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "invoice.docx"));

        GenerationResult result = service.generate("invoice", Map.of(), orgId, "pdf", "rpa");

        assertThat(result.body()).isEqualTo(pythonBytes);
        assertThat(result.channel()).isEqualTo("rpa");
        assertThat(result.format()).isEqualTo("docx");
    }

    @Test
    @DisplayName("generate(mode=rpa): Python кидает → fallback channel=rpa-fallback-error, body из PDF")
    void generate_givenRpaFails_whenCalled_thenFallsBackToProgrammatic() {
        when(pythonRpaClient.fill(eq("receipt-order"), any()))
                .thenThrow(new IllegalStateException("Python unreachable"));
        when(pdfService.generateReceiptOrderPdf(any())).thenReturn(new byte[]{9, 9});

        GenerationResult result = service.generate("receipt-order", Map.of(), orgId, "pdf", "rpa");

        assertThat(result.body()).containsExactly(9, 9);
        assertThat(result.channel()).isEqualTo("rpa-fallback-error");
        assertThat(result.format()).isEqualTo("pdf");
    }

    @Test
    @DisplayName("generate: неизвестный type → IllegalArgumentException")
    void generate_givenUnknownType_whenCalled_thenThrows() {
        assertThatThrownBy(() ->
                service.generate("totally-unknown", Map.of(), orgId, "pdf", "auto"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown document type");
    }

    @Test
    @DisplayName("generate: каждый из 11 типов делегирует в pdfService")
    void generate_givenAllTypes_whenCalled_thenDelegatesToPdfService() {
        when(pdfService.generateReceiptOrderPdf(any())).thenReturn(new byte[]{1});
        when(pdfService.generateInventoryListPdf(any())).thenReturn(new byte[]{1});
        when(pdfService.generateRevaluationActPdf(any())).thenReturn(new byte[]{1});
        when(pdfService.generateWriteOffActPdf(any())).thenReturn(new byte[]{1});
        when(pdfService.generateShippingInvoicePdf(any())).thenReturn(new byte[]{1});
        when(pdfService.generatePickingListPdf(any())).thenReturn(new byte[]{1});
        when(pdfService.generatePlacementListPdf(any())).thenReturn(new byte[]{1});
        when(pdfService.generateReceiptActPdf(any())).thenReturn(new byte[]{1});
        when(pdfService.generateInvoicePdf(any())).thenReturn(new byte[]{1});
        when(pdfService.generateTransportNotePdf(any())).thenReturn(new byte[]{1});
        when(pdfService.generateCmrPdf(any())).thenReturn(new byte[]{1});

        for (String type : new String[]{
                "receipt-order", "inventory-report", "revaluation-act", "write-off-act",
                "waybill", "picking-list", "placement-list", "receipt-act",
                "invoice", "transport-note", "cmr"}) {
            GenerationResult result = service.generate(type, Map.of(), orgId, "pdf", "auto");
            assertThat(result.body()).hasSize(1);
        }
    }

    @Test
    @DisplayName("generate(format=null): дефолтный pdf используется")
    void generate_givenNullFormat_whenCalled_thenDefaultsToPdf() {
        when(pdfService.generateReceiptOrderPdf(any())).thenReturn(new byte[]{1});

        GenerationResult result = service.generate("receipt-order", Map.of(), orgId, null, "auto");

        assertThat(result.format()).isEqualTo("pdf");
    }

    @Test
    @DisplayName("generate(format=xlsx/docx → workaround D-3): программный канал всегда PDF")
    void generate_givenXlsxFormat_whenCalled_thenFallsBackToPdf() {
        when(pdfService.generateReceiptOrderPdf(any())).thenReturn(new byte[]{3, 3});

        GenerationResult result = service.generate("receipt-order", Map.of(), orgId, "xlsx", "auto");

        assertThat(result.format()).isEqualTo("pdf");
        assertThat(result.channel()).isEqualTo("programmatic");
        assertThat(result.body()).containsExactly(3, 3);
    }
}
