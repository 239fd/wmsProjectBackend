package by.bsuir.documentservice.rpa;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfDocumentService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final HtmlPdfRenderer htmlPdfRenderer;

    public byte[] generateReceiptOrderPdf(Map<String, Object> data) {
        return htmlPdfRenderer.render("receipt-order", normalize(data));
    }

    public byte[] generateShippingInvoicePdf(Map<String, Object> data) {
        return htmlPdfRenderer.render("waybill", normalize(data));
    }

    public byte[] generateWriteOffActPdf(Map<String, Object> data) {
        return htmlPdfRenderer.render("write-off-act", normalize(data));
    }

    public byte[] generateRevaluationActPdf(Map<String, Object> data) {
        return htmlPdfRenderer.render("revaluation-act", normalize(data));
    }

    public byte[] generateInventoryListPdf(Map<String, Object> data) {
        return htmlPdfRenderer.render("inventory-report", normalize(data));
    }

    public byte[] generatePickingListPdf(Map<String, Object> data) {
        return htmlPdfRenderer.render("picking-list", normalize(data));
    }

    public byte[] generatePlacementListPdf(Map<String, Object> data) {
        return htmlPdfRenderer.render("placement-list", normalize(data));
    }

    public byte[] generateReceiptActPdf(Map<String, Object> data) {
        return htmlPdfRenderer.render("receipt-act", normalize(data));
    }

    public byte[] generateInvoicePdf(Map<String, Object> data) {
        Map<String, Object> m = normalize(data);
        m.putIfAbsent("documentNumber", data.getOrDefault("invoiceNumber", "—"));
        m.putIfAbsent("currency", "BYN");
        return htmlPdfRenderer.render("invoice", m);
    }

    public byte[] generateTransportNotePdf(Map<String, Object> data) {
        return htmlPdfRenderer.render("transport-note", normalize(data));
    }

    public byte[] generateCmrPdf(Map<String, Object> data) {
        Map<String, Object> m = normalize(data);
        m.putIfAbsent("documentNumber", data.getOrDefault("cmrNumber", "—"));
        m.putIfAbsent("currency", "EUR");
        return htmlPdfRenderer.render("cmr", m);
    }

    public byte[] generateAnalyticsReportPdf(Map<String, Object> data) {
        return htmlPdfRenderer.render("analytics-report", new HashMap<>(data));
    }

    private Map<String, Object> normalize(Map<String, Object> data) {
        Map<String, Object> m = new HashMap<>(data);
        m.putIfAbsent("documentDate", data.getOrDefault("date", today()));
        copyIfMissing(m, "consigneeName", "recipientName");
        copyIfMissing(m, "consigneeAddress", "recipientAddress");
        copyIfMissing(m, "shipperName", "senderName");
        copyIfMissing(m, "shipperAddress", "senderAddress");
        copyIfMissing(m, "shipperCountry", "senderCountry");
        copyIfMissing(m, "consigneeCountry", "recipientCountry");
        copyIfMissing(m, "placeOfLoading", "loadingPlace");
        copyIfMissing(m, "placeOfDelivery", "deliveryPlace");
        copyIfMissing(m, "grossWeightKg", "grossWeight");
        copyIfMissing(m, "totalWeight", "grossWeight");
        if (!m.containsKey("items") && m.get("productName") != null) {
            Map<String, Object> item = new HashMap<>();
            item.put("productName", m.get("productName"));
            item.put("sku", m.getOrDefault("sku", ""));
            item.put("unit", m.getOrDefault("unit", "шт"));
            item.put("quantity", m.getOrDefault("quantity", "0"));
            item.put("unitPrice", m.getOrDefault("unitPrice", "0.00"));
            item.put("totalPrice", m.getOrDefault("totalAmount", m.getOrDefault("totalPrice", "0.00")));
            item.put("batchNumber", m.getOrDefault("batchNumber", ""));
            m.put("items", java.util.List.of(item));
        }
        return m;
    }

    private void copyIfMissing(Map<String, Object> m, String target, String source) {
        if (m.get(target) == null && m.get(source) != null) {
            m.put(target, m.get(source));
        }
    }

    private String today() {
        return LocalDate.now().format(DATE_FMT);
    }
}
