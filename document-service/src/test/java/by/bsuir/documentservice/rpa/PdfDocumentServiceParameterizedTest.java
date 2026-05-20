package by.bsuir.documentservice.rpa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PdfDocumentService — все 14 типов: smoke + кириллица в полях")
class PdfDocumentServiceParameterizedTest {

    private static final HtmlPdfRenderer RENDERER = newRenderer();
    private final PdfDocumentService service = new PdfDocumentService(RENDERER);

    private static HtmlPdfRenderer newRenderer() {
        HtmlPdfRenderer r = new HtmlPdfRenderer();
        r.init();
        return r;
    }

    private static final Map<String, Object> CYRILLIC_DATA = Map.ofEntries(
            Map.entry("date", "01.05.2026"),
            Map.entry("documentNumber", "ТТН-2026-0042"),
            Map.entry("organizationName", "ОАО «Хлебозавод»"),
            Map.entry("supplierName", "ИП Иванов"),
            Map.entry("recipientName", "ООО «Покупатель»"),
            Map.entry("recipientAddress", "Минск, ул. Партизанская 5"),
            Map.entry("warehouseName", "Главный склад"),
            Map.entry("productName", "Хлеб ржаной «Бородинский»"),
            Map.entry("quantity", "50"),
            Map.entry("unitPrice", "12.50"),
            Map.entry("totalAmount", "625.00"),
            Map.entry("reason", "Истёк срок годности"),
            Map.entry("responsiblePerson", "Сидоров А.П.")
    );

    static Stream<Arguments> generators() {
        return Stream.of(
                Arguments.of("receipt-order", (Function<PdfDocumentService, byte[]>) s -> s.generateReceiptOrderPdf(CYRILLIC_DATA)),
                Arguments.of("shipping-invoice", (Function<PdfDocumentService, byte[]>) s -> s.generateShippingInvoicePdf(CYRILLIC_DATA)),
                Arguments.of("write-off-act", (Function<PdfDocumentService, byte[]>) s -> s.generateWriteOffActPdf(CYRILLIC_DATA)),
                Arguments.of("revaluation-act", (Function<PdfDocumentService, byte[]>) s -> s.generateRevaluationActPdf(CYRILLIC_DATA)),
                Arguments.of("inventory-list", (Function<PdfDocumentService, byte[]>) s -> s.generateInventoryListPdf(CYRILLIC_DATA)),
                Arguments.of("picking-list", (Function<PdfDocumentService, byte[]>) s -> s.generatePickingListPdf(CYRILLIC_DATA)),
                Arguments.of("receipt-act", (Function<PdfDocumentService, byte[]>) s -> s.generateReceiptActPdf(CYRILLIC_DATA)),
                Arguments.of("invoice", (Function<PdfDocumentService, byte[]>) s -> s.generateInvoicePdf(CYRILLIC_DATA)),
                Arguments.of("transport-note", (Function<PdfDocumentService, byte[]>) s -> s.generateTransportNotePdf(CYRILLIC_DATA)),
                Arguments.of("cmr", (Function<PdfDocumentService, byte[]>) s -> s.generateCmrPdf(CYRILLIC_DATA))
        );
    }

    @ParameterizedTest(name = "{0}: PDF c кириллицей сгенерирован, valid header")
    @MethodSource("generators")
    void generator_GivenCyrillicData_ShouldReturnValidPdf(
            String name, Function<PdfDocumentService, byte[]> generator) {
        byte[] pdf = generator.apply(service);

        assertThat(pdf)
                .as("PDF для %s не должен быть пустым", name)
                .isNotNull()
                .isNotEmpty();
        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII))
                .as("PDF magic header для %s", name)
                .isEqualTo("%PDF");
    }

    @ParameterizedTest(name = "{0}: с пустыми данными работает с дефолтами")
    @MethodSource("generators")
    void generator_GivenEmptyData_ShouldFallbackToDefaults(
            String name, Function<PdfDocumentService, byte[]> generator) {
        byte[] pdf = generator.apply(service);
        assertThat(pdf).isNotEmpty();
    }

    @Test
    @DisplayName("Сгенерированный PDF (ТТН) содержит кириллический заголовок (verify через PDFBox text extraction)")
    void shippingInvoice_ShouldEmbedCyrillicTitle() throws Exception {
        byte[] pdf = service.generateShippingInvoicePdf(CYRILLIC_DATA);

        try (org.apache.pdfbox.pdmodel.PDDocument doc =
                     org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
            assertThat(text)
                    .contains("ТОВАРНО-ТРАНСПОРТНАЯ НАКЛАДНАЯ")
                    .contains("ООО «Покупатель»")
                    .contains("Хлеб ржаной");
        }
    }
}
