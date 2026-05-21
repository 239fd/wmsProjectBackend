package by.bsuir.documentservice.service;

import by.bsuir.documentservice.dto.InventoryListData;
import by.bsuir.documentservice.dto.CmrData;
import by.bsuir.documentservice.dto.InvoiceData;
import by.bsuir.documentservice.dto.ReceiptActData;
import by.bsuir.documentservice.dto.TransportNoteData;
import by.bsuir.documentservice.dto.ReceiptOrderData;
import by.bsuir.documentservice.dto.RevaluationActData;
import by.bsuir.documentservice.dto.ShippingInvoiceData;
import by.bsuir.documentservice.dto.WriteOffActData;
import by.bsuir.documentservice.rpa.DocumentRpaService;
import by.bsuir.documentservice.rpa.PdfDocumentService;
import by.bsuir.documentservice.rpa.PythonRpaClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    public record GenerationResult(byte[] body, String channel, String format) { }

    private final DocumentRpaService rpaService;
    private final PdfDocumentService pdfService;
    private final DataEnrichmentService enrichmentService;
    private final PythonRpaClient pythonRpaClient;

    public byte[] generate(String type, Map<String, Object> data, UUID organizationId, String format) {
        return generate(type, data, organizationId, format, "auto").body();
    }

    public GenerationResult generate(
            String type, Map<String, Object> data, UUID organizationId, String format, String mode) {
        String effectiveFormat = format != null ? format : "pdf";
        Map<String, Object> enriched = enrichmentService.enrich(data, organizationId);

        if ("rpa".equalsIgnoreCase(mode)) {
            try {
                PythonRpaClient.FillResponse rpa = pythonRpaClient.fill(type, enriched);
                String fmt = extOf(rpa.filename(), effectiveFormat);
                log.info("RPA (Python): {} bytes for type={}, format={}",
                        rpa.body() != null ? rpa.body().length : 0, type, fmt);
                return new GenerationResult(rpa.body(), "rpa", fmt);
            } catch (Exception e) {
                log.warn("Python RPA failed for {} ({}), fallback to PDF", type, e.getMessage());
                return new GenerationResult(
                        generateViaPdf(type, enriched),
                        "rpa-fallback-error", "pdf");
            }
        }

        return new GenerationResult(
                generateViaPdf(type, enriched),
                "programmatic", "pdf");
    }

    private String extOf(String name, String fallback) {
        if (name == null) return fallback;
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(idx + 1).toLowerCase() : fallback;
    }

    private byte[] generateViaPdf(String type, Map<String, Object> data) {
        return switch (type) {
            case "receipt-order" -> pdfService.generateReceiptOrderPdf(data);
            case "inventory-report" -> pdfService.generateInventoryListPdf(data);
            case "revaluation-act" -> pdfService.generateRevaluationActPdf(data);
            case "write-off-act" -> pdfService.generateWriteOffActPdf(data);
            case "waybill" -> pdfService.generateShippingInvoicePdf(data);
            case "picking-list" -> pdfService.generatePickingListPdf(data);
            case "placement-list" -> pdfService.generatePlacementListPdf(data);
            case "receipt-act" -> pdfService.generateReceiptActPdf(data);
            case "invoice" -> pdfService.generateInvoicePdf(data);
            case "transport-note" -> pdfService.generateTransportNotePdf(data);
            case "cmr" -> pdfService.generateCmrPdf(data);
            case "analytics-report" -> pdfService.generateAnalyticsReportPdf(data);
            default -> throw new IllegalArgumentException("Unknown document type: " + type);
        };
    }

    private byte[] generateViaRpa(String type, Map<String, Object> data) {
        try {
            return switch (type) {
                case "receipt-order" -> rpaService.generateReceiptOrder(mapToReceiptOrderData(data));
                case "revaluation-act" -> rpaService.generateRevaluationAct(mapToRevaluationActData(data));
                case "inventory-report" -> rpaService.generateInventoryList(mapToInventoryListData(data));
                case "write-off-act" -> rpaService.generateWriteOffAct(mapToWriteOffActData(data));
                case "waybill" -> rpaService.generateShippingInvoice(mapToShippingInvoiceData(data));
                case "receipt-act" -> rpaService.generateReceiptAct(mapToReceiptActData(data));
                case "invoice" -> rpaService.generateInvoice(mapToInvoiceData(data));
                case "transport-note" -> rpaService.generateTransportNote(mapToTransportNoteData(data));
                case "cmr" -> rpaService.generateCmr(mapToCmrData(data));
                default -> throw new IllegalArgumentException("RPA template not available for type: " + type);
            };
        } catch (Exception e) {
            log.error("RPA generation failed for {}: {}", type, e.getMessage(), e);
            throw new IllegalStateException("RPA generation failed: " + e.getMessage(), e);
        }
    }

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

    @SuppressWarnings("unchecked")
    private ReceiptActData mapToReceiptActData(Map<String, Object> data) {
        List<ReceiptActData.DiscrepancyItem> discrepancies = new ArrayList<>();
        Object rawList = data.get("discrepancies");
        if (rawList instanceof List<?> rawItems) {
            int idx = 1;
            for (Object raw : rawItems) {
                if (!(raw instanceof Map<?, ?> map)) continue;
                Map<String, Object> item = (Map<String, Object>) map;
                discrepancies.add(ReceiptActData.DiscrepancyItem.builder()
                        .rowNumber(idx++)
                        .productName(getString(item, "productName", "—"))
                        .sku(getString(item, "sku", "—"))
                        .unit(getString(item, "unit", "шт"))
                        .expectedQty(toDecimal(item.get("expectedQty")))
                        .actualQty(toDecimal(item.get("actualQty")))
                        .differenceQty(diff(item.get("expectedQty"), item.get("actualQty")))
                        .discrepancyType(getString(item, "discrepancyType", ""))
                        .defectDescription(getString(item, "defectDescription", ""))
                        .build());
            }
        }

        return ReceiptActData.builder()
                .documentNumber(getString(data, "documentNumber", "АП-001"))
                .documentDate(getDate(data, "documentDate", LocalDate.now()))
                .organizationName(getString(data, "organizationName", "ООО Компания"))
                .inn(getString(data, "inn", "—"))
                .warehouseName(getString(data, "warehouseName", "—"))
                .supplierName(getString(data, "supplierName", "—"))
                .supplierInn(getString(data, "supplierInn", "—"))
                .contractNumber(getString(data, "contractNumber", "—"))
                .contractDate(getString(data, "contractDate", "—"))
                .waybillNumber(getString(data, "waybillNumber", "—"))
                .waybillDate(getString(data, "waybillDate", "—"))
                .chairmanName(getString(data, "chairmanName", "—"))
                .commissionMembers((List<String>) data.getOrDefault("commissionMembers", new ArrayList<>()))
                .acceptedBy(getString(data, "acceptedBy", "—"))
                .approvedBy(getString(data, "approvedBy", "—"))
                .generalNotes(getString(data, "generalNotes", ""))
                .discrepancies(discrepancies)
                .build();
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal diff(Object expected, Object actual) {
        return toDecimal(actual).subtract(toDecimal(expected));
    }

    @SuppressWarnings("unchecked")
    private InvoiceData mapToInvoiceData(Map<String, Object> data) {
        List<InvoiceData.InvoiceItem> items = new ArrayList<>();
        Object rawList = data.get("items");
        if (rawList instanceof List<?> rawItems) {
            int idx = 1;
            for (Object raw : rawItems) {
                if (!(raw instanceof Map<?, ?> map)) continue;
                Map<String, Object> item = (Map<String, Object>) map;
                BigDecimal qty = toDecimal(item.get("quantity"));
                BigDecimal unitPrice = toDecimal(item.get("unitPrice"));
                BigDecimal totalPrice = item.get("totalPrice") != null
                        ? toDecimal(item.get("totalPrice"))
                        : qty.multiply(unitPrice);
                items.add(InvoiceData.InvoiceItem.builder()
                        .rowNumber(idx++)
                        .productName(getString(item, "productName", "—"))
                        .sku(getString(item, "sku", "—"))
                        .unit(getString(item, "unit", "шт"))
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .totalPrice(totalPrice)
                        .build());
            }
        }

        BigDecimal totalAmount = items.stream()
                .map(InvoiceData.InvoiceItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return InvoiceData.builder()
                .documentNumber(getString(data, "documentNumber", "И-001"))
                .documentDate(getDate(data, "documentDate", LocalDate.now()))
                .currency(getString(data, "currency", "BYN"))
                .sellerName(getString(data, "sellerName", "—"))
                .sellerInn(getString(data, "sellerInn", "—"))
                .sellerAddress(getString(data, "sellerAddress", "—"))
                .buyerName(getString(data, "buyerName", "—"))
                .buyerInn(getString(data, "buyerInn", "—"))
                .buyerAddress(getString(data, "buyerAddress", "—"))
                .contractNumber(getString(data, "contractNumber", "—"))
                .contractDate(getString(data, "contractDate", "—"))
                .items(items)
                .totalAmount(totalAmount)
                .totalAmountInWords(getString(data, "totalAmountInWords", ""))
                .vatRate(toDecimal(data.get("vatRate")))
                .vatAmount(toDecimal(data.get("vatAmount")))
                .responsiblePerson(getString(data, "responsiblePerson", "—"))
                .notes(getString(data, "notes", ""))
                .build();
    }

    @SuppressWarnings("unchecked")
    private TransportNoteData mapToTransportNoteData(Map<String, Object> data) {
        List<TransportNoteData.TransportItem> items = new ArrayList<>();
        Object rawList = data.get("items");
        if (rawList instanceof List<?> rawItems) {
            int idx = 1;
            for (Object raw : rawItems) {
                if (!(raw instanceof Map<?, ?> map)) continue;
                Map<String, Object> item = (Map<String, Object>) map;
                BigDecimal qty = toDecimal(item.get("quantity"));
                BigDecimal unitPrice = toDecimal(item.get("unitPrice"));
                BigDecimal totalPrice = item.get("totalPrice") != null
                        ? toDecimal(item.get("totalPrice"))
                        : qty.multiply(unitPrice);
                items.add(TransportNoteData.TransportItem.builder()
                        .rowNumber(idx++)
                        .productName(getString(item, "productName", "—"))
                        .sku(getString(item, "sku", "—"))
                        .unit(getString(item, "unit", "шт"))
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .totalPrice(totalPrice)
                        .vatRate(toDecimal(item.get("vatRate")))
                        .vatAmount(toDecimal(item.get("vatAmount")))
                        .build());
            }
        }

        BigDecimal totalQty = items.stream()
                .map(TransportNoteData.TransportItem::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = items.stream()
                .map(TransportNoteData.TransportItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalVat = items.stream()
                .map(TransportNoteData.TransportItem::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return TransportNoteData.builder()
                .layout(getString(data, "layout", "horizontal"))
                .documentNumber(getString(data, "documentNumber", "ТН-001"))
                .documentDate(getDate(data, "documentDate", LocalDate.now()))
                .currency(getString(data, "currency", "BYN"))
                .shipperName(getString(data, "shipperName", "—"))
                .shipperInn(getString(data, "shipperInn", "—"))
                .shipperAddress(getString(data, "shipperAddress", "—"))
                .consigneeName(getString(data, "consigneeName", "—"))
                .consigneeInn(getString(data, "consigneeInn", "—"))
                .consigneeAddress(getString(data, "consigneeAddress", "—"))
                .warehouseName(getString(data, "warehouseName", "—"))
                .contractNumber(getString(data, "contractNumber", "—"))
                .contractDate(getString(data, "contractDate", "—"))
                .waybillReference(getString(data, "waybillReference", "—"))
                .items(items)
                .totalQuantity(totalQty)
                .totalAmount(totalAmount)
                .totalVat(totalVat)
                .releasedBy(getString(data, "releasedBy", "—"))
                .acceptedBy(getString(data, "acceptedBy", "—"))
                .notes(getString(data, "notes", ""))
                .build();
    }

    @SuppressWarnings("unchecked")
    private CmrData mapToCmrData(Map<String, Object> data) {
        List<CmrData.CmrItem> items = new ArrayList<>();
        Object rawList = data.get("items");
        if (rawList instanceof List<?> rawItems) {
            int idx = 1;
            for (Object raw : rawItems) {
                if (!(raw instanceof Map<?, ?> map)) continue;
                Map<String, Object> item = (Map<String, Object>) map;
                items.add(CmrData.CmrItem.builder()
                        .rowNumber(idx++)
                        .marks(getString(item, "marks", "—"))
                        .packagingType(getString(item, "packagingType", "—"))
                        .productName(getString(item, "productName", "—"))
                        .hsCode(getString(item, "hsCode", "—"))
                        .quantity(toDecimal(item.get("quantity")))
                        .unit(getString(item, "unit", "шт"))
                        .grossWeightKg(toDecimal(item.get("grossWeightKg")))
                        .volumeM3(toDecimal(item.get("volumeM3")))
                        .declaredValue(toDecimal(item.get("declaredValue")))
                        .build());
            }
        }

        BigDecimal totalQty = items.stream()
                .map(CmrData.CmrItem::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalWeight = items.stream()
                .map(CmrData.CmrItem::getGrossWeightKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalVolume = items.stream()
                .map(CmrData.CmrItem::getVolumeM3)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDeclared = items.stream()
                .map(CmrData.CmrItem::getDeclaredValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CmrData.builder()
                .documentNumber(getString(data, "documentNumber", "CMR-001"))
                .documentDate(getDate(data, "documentDate", LocalDate.now()))
                .currency(getString(data, "currency", "EUR"))
                .shipperName(getString(data, "shipperName", "—"))
                .shipperInn(getString(data, "shipperInn", "—"))
                .shipperAddress(getString(data, "shipperAddress", "—"))
                .shipperCountry(getString(data, "shipperCountry", "BY"))
                .shipperGln(getString(data, "shipperGln", ""))
                .consigneeName(getString(data, "consigneeName", "—"))
                .consigneeInn(getString(data, "consigneeInn", "—"))
                .consigneeAddress(getString(data, "consigneeAddress", "—"))
                .consigneeCountry(getString(data, "consigneeCountry", "RU"))
                .consigneeGln(getString(data, "consigneeGln", ""))
                .carrierName(getString(data, "carrierName", "—"))
                .carrierAddress(getString(data, "carrierAddress", "—"))
                .vehicleNumber(getString(data, "vehicleNumber", "—"))
                .trailerNumber(getString(data, "trailerNumber", ""))
                .driverName(getString(data, "driverName", "—"))
                .driverPassport(getString(data, "driverPassport", "—"))
                .placeOfLoading(getString(data, "placeOfLoading", "—"))
                .placeOfDelivery(getString(data, "placeOfDelivery", "—"))
                .loadingDate(getDate(data, "loadingDate", LocalDate.now()))
                .deliveryDate(getDate(data, "deliveryDate", LocalDate.now()))
                .items(items)
                .totalQuantity(totalQty)
                .totalWeight(totalWeight)
                .totalVolume(totalVolume)
                .cargoDeclaredValue(data.get("cargoDeclaredValue") != null
                        ? toDecimal(data.get("cargoDeclaredValue"))
                        : totalDeclared)
                .paymentInstructions(getString(data, "paymentInstructions", ""))
                .specialInstructions(getString(data, "specialInstructions", ""))
                .contractNumber(getString(data, "contractNumber", "—"))
                .shipperSignedBy(getString(data, "shipperSignedBy", "—"))
                .carrierSignedBy(getString(data, "carrierSignedBy", "—"))
                .consigneeSignedBy(getString(data, "consigneeSignedBy", "—"))
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
        if (value instanceof String s) {
            return LocalDate.parse(s);
        }
        return defaultValue;
    }
}
