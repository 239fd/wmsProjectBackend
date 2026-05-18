package by.bsuir.documentservice.service;

import by.bsuir.documentservice.config.RpaProperties;
import by.bsuir.documentservice.rpa.DocumentRpaService;
import by.bsuir.documentservice.rpa.OfficeDocumentBot;
import by.bsuir.documentservice.rpa.PdfDocumentService;
import by.bsuir.documentservice.rpa.RpaTemplateBinding;
import by.bsuir.documentservice.service.DocumentService.GenerationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private RpaTemplateBinding rpaTemplateBinding;

    @Mock
    private ObjectProvider<OfficeDocumentBot> officeBotProvider;

    @Mock
    private RpaProperties rpaProperties;

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
    @DisplayName("generate(format=rpa-xls): использует RPA POI generator")
    void generate_givenRpaXlsFormat_whenCalled_thenUsesPoi() {
        when(rpaService.generateReceiptOrder(any())).thenReturn(new byte[]{5, 5});

        GenerationResult result = service.generate("receipt-order", Map.of(), orgId, "rpa-xls", "auto");

        assertThat(result.body()).containsExactly(5, 5);
        assertThat(result.format()).isEqualTo("rpa-xls");
    }

    @Test
    @DisplayName("generate(rpa-mode): бот недоступен → fallback channel=rpa-fallback-disabled")
    void generate_givenRpaModeNoBot_whenCalled_thenFallsBackDisabled() {
        when(officeBotProvider.getIfAvailable()).thenReturn(null);
        when(pdfService.generateReceiptOrderPdf(any())).thenReturn(new byte[]{1});

        GenerationResult result = service.generate("receipt-order", Map.of(), orgId, "pdf", "rpa");

        assertThat(result.channel()).isEqualTo("rpa-fallback-disabled");
    }

    @Test
    @DisplayName("generate(rpa-mode): нет binding → fallback channel=rpa-fallback-unsupported-type")
    void generate_givenRpaModeNoBinding_whenCalled_thenFallsBackUnsupported() {
        OfficeDocumentBot bot = Mockito.mock(OfficeDocumentBot.class);
        when(officeBotProvider.getIfAvailable()).thenReturn(bot);
        when(rpaTemplateBinding.bind(any(), any())).thenReturn(null);
        when(pdfService.generateReceiptOrderPdf(any())).thenReturn(new byte[]{1});

        GenerationResult result = service.generate("receipt-order", Map.of(), orgId, "pdf", "rpa");

        assertThat(result.channel()).isEqualTo("rpa-fallback-unsupported-type");
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
    @DisplayName("generate(rpa-xls для unknown): RPA generation failed")
    void generate_givenUnknownTypeRpaFormat_whenCalled_thenThrows() {
        assertThatThrownBy(() ->
                service.generate("totally-unknown", Map.of(), orgId, "rpa-xls", "auto"))
                .isInstanceOf(IllegalStateException.class);
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
    @DisplayName("generate(rpa-xls для каждого RPA-маппера): все 9 mapToXxxData проходят")
    void generate_givenRpaXlsForEachType_whenCalled_thenInvokesMapper() {
        when(rpaService.generateReceiptOrder(any())).thenReturn(new byte[]{1});
        when(rpaService.generateRevaluationAct(any())).thenReturn(new byte[]{1});
        when(rpaService.generateInventoryList(any())).thenReturn(new byte[]{1});
        when(rpaService.generateWriteOffAct(any())).thenReturn(new byte[]{1});
        when(rpaService.generateShippingInvoice(any())).thenReturn(new byte[]{1});
        when(rpaService.generateReceiptAct(any())).thenReturn(new byte[]{1});
        when(rpaService.generateInvoice(any())).thenReturn(new byte[]{1});
        when(rpaService.generateTransportNote(any())).thenReturn(new byte[]{1});
        when(rpaService.generateCmr(any())).thenReturn(new byte[]{1});

        // payload with realistic mix of fields to exercise mapper branches:
        Map<String, Object> rich = new java.util.HashMap<>();
        rich.put("documentNumber", "TEST-001");
        rich.put("documentDate", "2026-05-15");
        rich.put("organizationName", "ООО ВМС");
        rich.put("supplierName", "ОАО Молоко");
        rich.put("warehouseName", "Склад №1");
        java.util.Map<String, Object> itemMap = new java.util.HashMap<>();
        itemMap.put("productName", "p");
        itemMap.put("quantity", 5);
        itemMap.put("price", "10.50");
        itemMap.put("amount", "52.50");
        itemMap.put("batchNumber", "B-1");
        itemMap.put("expectedQuantity", "10");
        itemMap.put("actualQuantity", "8");
        itemMap.put("discrepancy", "-2");
        itemMap.put("rowNumber", 1);
        itemMap.put("unitPrice", "10");
        itemMap.put("vatRate", "20");
        itemMap.put("vatAmount", "10.5");
        itemMap.put("totalWithVat", "63");
        itemMap.put("unit", "шт");
        rich.put("items", java.util.List.of(itemMap));
        rich.put("commission", java.util.List.of("Иванов И.И.", "Петров П.П."));

        for (String type : new String[]{
                "receipt-order", "revaluation-act", "inventory-report",
                "write-off-act", "waybill", "receipt-act",
                "invoice", "transport-note", "cmr"}) {
            GenerationResult result = service.generate(type, rich, orgId, "rpa-xls", "auto");
            assertThat(result.body()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("generate(rpa-xls): rpaService бросает → IllegalStateException")
    void generate_givenRpaFailure_whenCalled_thenWraps() {
        when(rpaService.generateReceiptOrder(any())).thenThrow(new RuntimeException("POI fail"));

        assertThatThrownBy(() ->
                service.generate("receipt-order", Map.of(), orgId, "rpa-xls", "auto"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RPA generation failed");
    }

    @Test
    @DisplayName("generate(rpa-docx как format): то же что rpa-xls — идёт в RPA")
    void generate_givenRpaDocxFormat_whenCalled_thenUsesRpa() {
        when(rpaService.generateWriteOffAct(any())).thenReturn(new byte[]{1, 2});

        GenerationResult result = service.generate("write-off-act", Map.of(), orgId, "rpa-docx", "auto");

        assertThat(result.format()).isEqualTo("rpa-docx");
        assertThat(result.body()).hasSize(2);
    }
}
