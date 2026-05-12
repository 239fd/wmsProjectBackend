package by.bsuir.documentservice.service;

import by.bsuir.documentservice.dto.InventoryListData;
import by.bsuir.documentservice.dto.ReceiptOrderData;
import by.bsuir.documentservice.dto.RevaluationActData;
import by.bsuir.documentservice.dto.ShippingInvoiceData;
import by.bsuir.documentservice.dto.WriteOffActData;
import by.bsuir.documentservice.rpa.DocumentRpaService;
import by.bsuir.documentservice.rpa.PdfDocumentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRpaService rpaService;
    private final PdfDocumentService pdfService;
    private final DataEnrichmentService enrichmentService;

    private final Map<UUID, DocumentRecord> records = new ConcurrentHashMap<>();

    public record DocumentRecord(
            UUID id,
            String type,
            String format,
            UUID organizationId,
            UUID generatedBy,
            LocalDateTime generatedAt,
            Map<String, Object> payload) {
    }

    public UUID generateReceiptOrder(Map<String, Object> data, UUID organizationId, UUID userId, String format) {
        return generate("receipt-order", data, organizationId, userId, format);
    }

    public UUID generateShipmentOrder(Map<String, Object> data, UUID organizationId, UUID userId, String format) {
        return generate("release-order", data, organizationId, userId, format);
    }

    public UUID generateInventoryReport(Map<String, Object> data, UUID organizationId, UUID userId, String format) {
        return generate("inventory-report", data, organizationId, userId, format);
    }

    public UUID generateRevaluationAct(Map<String, Object> data, UUID organizationId, UUID userId, String format) {
        return generate("revaluation-act", data, organizationId, userId, format);
    }

    public UUID generateWriteOffAct(Map<String, Object> data, UUID organizationId, UUID userId, String format) {
        return generate("write-off-act", data, organizationId, userId, format);
    }

    public UUID generateWaybill(Map<String, Object> data, UUID organizationId, UUID userId, String format) {
        return generate("waybill", data, organizationId, userId, format);
    }

    public UUID generatePickingList(Map<String, Object> data, UUID organizationId, UUID userId, String format) {
        return generate("picking-list", data, organizationId, userId, format);
    }

    public UUID generateReceiptAct(Map<String, Object> data, UUID organizationId, UUID userId, String format) {
        return generate("receipt-act", data, organizationId, userId, format);
    }

    public UUID generateInvoiceFact(Map<String, Object> data, UUID organizationId, UUID userId, String format) {
        return generate("invoice-fact", data, organizationId, userId, format);
    }

    public UUID generateInvoice(Map<String, Object> data, UUID organizationId, UUID userId, String format) {
        return generate("invoice", data, organizationId, userId, format);
    }

    public UUID generateTransportNote(Map<String, Object> data, UUID organizationId, UUID userId, String format) {
        return generate("transport-note", data, organizationId, userId, format);
    }

    public UUID generateCmr(Map<String, Object> data, UUID organizationId, UUID userId, String format) {
        return generate("cmr", data, organizationId, userId, format);
    }

    public UUID generateDiscrepancyAct(Map<String, Object> data, UUID organizationId, UUID userId, String format) {
        return generate("discrepancy-act", data, organizationId, userId, format);
    }

    private UUID generate(String type, Map<String, Object> data, UUID organizationId, UUID userId, String format) {
        UUID id = UUID.randomUUID();
        String effectiveFormat = format != null ? format : "pdf";
        Map<String, Object> enriched = enrichmentService.enrich(data, organizationId);
        records.put(id, new DocumentRecord(id, type, effectiveFormat, organizationId, userId, LocalDateTime.now(), enriched));
        log.info("Generated document record: id={}, type={}, format={}, org={}", id, type, effectiveFormat, organizationId);
        return id;
    }

    public byte[] getDocument(UUID documentId, UUID organizationId) {
        DocumentRecord record = findOwned(documentId, organizationId);
        return regenerate(record);
    }

    public Map<String, Object> getDocumentMetadata(UUID documentId, UUID organizationId) {
        DocumentRecord record = findOwned(documentId, organizationId);
        Map<String, Object> meta = new HashMap<>();
        meta.put("id", record.id().toString());
        meta.put("type", record.type());
        meta.put("format", record.format());
        meta.put("organizationId", record.organizationId());
        meta.put("generatedBy", record.generatedBy());
        meta.put("generatedAt", record.generatedAt().toString());
        return meta;
    }

    public Map<String, Object> getAllDocuments(int page, int size, UUID organizationId) {
        List<Map<String, Object>> docs = new ArrayList<>();
        records.values().stream()
                .filter(r -> organizationId == null
                        || r.organizationId() == null
                        || organizationId.equals(r.organizationId()))
                .forEach(r -> {
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("id", r.id().toString());
                    doc.put("type", r.type());
                    doc.put("format", r.format());
                    doc.put("organizationId", r.organizationId());
                    doc.put("generatedAt", r.generatedAt().toString());
                    docs.add(doc);
                });
        int start = page * size;
        int end = Math.min(start + size, docs.size());
        List<Map<String, Object>> pageDocs = start < docs.size() ? docs.subList(start, end) : new ArrayList<>();
        Map<String, Object> result = new HashMap<>();
        result.put("documents", pageDocs);
        result.put("page", page);
        result.put("size", size);
        result.put("total", docs.size());
        return result;
    }

    private DocumentRecord findOwned(UUID documentId, UUID organizationId) {
        DocumentRecord record = records.get(documentId);
        if (record == null) {
            throw new RuntimeException("Document not found: " + documentId);
        }
        if (organizationId != null && record.organizationId() != null
                && !organizationId.equals(record.organizationId())) {
            throw new RuntimeException("Document belongs to another organization");
        }
        return record;
    }

    private byte[] regenerate(DocumentRecord record) {
        Map<String, Object> data = record.payload();
        String format = record.format();

        if ("rpa-xls".equals(format) || "rpa-docx".equals(format)) {
            return regenerateViaRpa(record);
        }

        return switch (record.type()) {
            case "receipt-order" -> pdfService.generateReceiptOrderPdf(data);
            case "release-order" -> pdfService.generateReleaseOrderPdf(data);
            case "inventory-report" -> pdfService.generateInventoryListPdf(data);
            case "revaluation-act" -> pdfService.generateRevaluationActPdf(data);
            case "write-off-act" -> pdfService.generateWriteOffActPdf(data);
            case "waybill" -> pdfService.generateShippingInvoicePdf(data);
            case "picking-list" -> pdfService.generatePickingListPdf(data);
            case "receipt-act" -> pdfService.generateReceiptActPdf(data);
            case "invoice-fact" -> pdfService.generateInvoiceFactPdf(data);
            case "invoice" -> pdfService.generateInvoicePdf(data);
            case "transport-note" -> pdfService.generateTransportNotePdf(data);
            case "cmr" -> pdfService.generateCmrPdf(data);
            case "discrepancy-act" -> pdfService.generateDiscrepancyActPdf(data);
            default -> throw new RuntimeException("Unknown document type: " + record.type());
        };
    }

    @SuppressWarnings("unchecked")
    private byte[] regenerateViaRpa(DocumentRecord record) {
        Map<String, Object> data = record.payload();
        try {
            return switch (record.type()) {
                case "receipt-order" -> rpaService.generateReceiptOrder(mapToReceiptOrderData(data));
                case "revaluation-act" -> rpaService.generateRevaluationAct(mapToRevaluationActData(data));
                case "inventory-report" -> rpaService.generateInventoryList(mapToInventoryListData(data));
                case "write-off-act" -> rpaService.generateWriteOffAct(mapToWriteOffActData(data));
                case "waybill" -> rpaService.generateShippingInvoice(mapToShippingInvoiceData(data));
                default -> throw new RuntimeException("RPA template not available for type: " + record.type());
            };
        } catch (Exception e) {
            log.error("RPA regeneration failed: {}", e.getMessage(), e);
            throw new RuntimeException("RPA regeneration failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private ReceiptOrderData mapToReceiptOrderData(Map<String, Object> data) {
        return ReceiptOrderData.builder()
                .documentNumber(getString(data, "documentNumber", "ПО-001"))
                .documentDate(getDate(data, "documentDate", LocalDate.now()))
                .organizationName(getString(data, "organizationName", "ООО Компания"))
                .inn(getString(data, "inn", "1234567890"))
                .warehouseName(getString(data, "warehouseName", "Склад №1"))
                .items(new ArrayList<>())
                .totalQuantity(0)
                .totalAmount(BigDecimal.ZERO)
                .build();
    }

    @SuppressWarnings("unchecked")
    private RevaluationActData mapToRevaluationActData(Map<String, Object> data) {
        return RevaluationActData.builder()
                .documentNumber(getString(data, "documentNumber", "АП-001"))
                .documentDate(getDate(data, "documentDate", LocalDate.now()))
                .organizationName(getString(data, "organizationName", "ООО Компания"))
                .inn(getString(data, "inn", "1234567890"))
                .warehouseName(getString(data, "warehouseName", "Склад №1"))
                .reason(getString(data, "reason", "INFLATION"))
                .reasonDescription(getString(data, "reasonDescription", "Переоценка товаров"))
                .chairmanName(getString(data, "chairmanName", "Председатель комиссии"))
                .commissionMembers((List<String>) data.getOrDefault("commissionMembers", new ArrayList<>()))
                .items(new ArrayList<>())
                .totalOldValue(BigDecimal.ZERO)
                .totalNewValue(BigDecimal.ZERO)
                .totalDifference(BigDecimal.ZERO)
                .build();
    }

    @SuppressWarnings("unchecked")
    private InventoryListData mapToInventoryListData(Map<String, Object> data) {
        return InventoryListData.builder()
                .documentNumber(getString(data, "documentNumber", "ИО-001"))
                .documentDate(getDate(data, "documentDate", LocalDate.now()))
                .inventoryDate(getDate(data, "inventoryDate", LocalDate.now()))
                .organizationName(getString(data, "organizationName", "ООО Компания"))
                .inn(getString(data, "inn", "1234567890"))
                .warehouseName(getString(data, "warehouseName", "Склад №1"))
                .chairmanName(getString(data, "chairmanName", "Председатель комиссии"))
                .commissionMembers((List<String>) data.getOrDefault("commissionMembers", new ArrayList<>()))
                .responsiblePerson(getString(data, "responsiblePerson", "Материально ответственное лицо"))
                .items(new ArrayList<>())
                .totalBookValue(BigDecimal.ZERO)
                .totalActualValue(BigDecimal.ZERO)
                .totalDifference(BigDecimal.ZERO)
                .build();
    }

    private WriteOffActData mapToWriteOffActData(Map<String, Object> data) {
        return WriteOffActData.builder()
                .documentNumber(getString(data, "documentNumber", "АС-001"))
                .documentDate(getDate(data, "documentDate", LocalDate.now()))
                .organizationName(getString(data, "organizationName", "ООО Компания"))
                .reason(getString(data, "reason", "DAMAGE"))
                .items(new ArrayList<>())
                .build();
    }

    private ShippingInvoiceData mapToShippingInvoiceData(Map<String, Object> data) {
        return ShippingInvoiceData.builder()
                .invoiceNumber(getString(data, "invoiceNumber", "ТТН-001"))
                .invoiceDate(getDate(data, "invoiceDate", LocalDate.now()))
                .shipperName(getString(data, "shipperName", "ООО Грузоотправитель"))
                .consigneeName(getString(data, "consigneeName", "ООО Грузополучатель"))
                .items(new ArrayList<>())
                .totalQuantity(0)
                .totalWeight(0.0)
                .totalVolume(0.0)
                .totalCost(0.0)
                .build();
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private LocalDate getDate(Map<String, Object> map, String key, LocalDate defaultValue) {
        Object value = map.get(key);
        if (value instanceof String) {
            return LocalDate.parse((String) value);
        }
        return defaultValue;
    }
}
