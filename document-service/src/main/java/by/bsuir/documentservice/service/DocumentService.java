package by.bsuir.documentservice.service;

import by.bsuir.documentservice.dto.InventoryListData;
import by.bsuir.documentservice.dto.ReceiptOrderData;
import by.bsuir.documentservice.dto.RevaluationActData;
import by.bsuir.documentservice.dto.ShippingInvoiceData;
import by.bsuir.documentservice.dto.WriteOffActData;
import by.bsuir.documentservice.rpa.DocumentRpaService;
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

    private final Map<UUID, byte[]> documentStorage = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Object>> documentMetadata = new ConcurrentHashMap<>();

    public UUID generateReceiptOrder(Map<String, Object> data) {
        log.info("Generating receipt order using RPA");
        log.debug("Receipt order data: {}", data);

        UUID documentId = UUID.randomUUID();

        try {
            ReceiptOrderData receiptData = mapToReceiptOrderData(data);

            byte[] documentBytes = rpaService.generateReceiptOrder(receiptData);

            documentStorage.put(documentId, documentBytes);
            documentMetadata.put(documentId, createMetadata("receipt-order", "generated"));

            log.info("Receipt order generated successfully via RPA: {}", documentId);
        } catch (Exception e) {
            log.error("Error generating receipt order via RPA", e);
            documentMetadata.put(documentId, createMetadata("receipt-order", "stub"));
        }

        return documentId;
    }

    public UUID generateRevaluationAct(Map<String, Object> data) {
        log.info("Generating revaluation act using RPA");
        log.debug("Revaluation act data: {}", data);

        UUID documentId = UUID.randomUUID();

        try {
            RevaluationActData revaluationData = mapToRevaluationActData(data);

            byte[] documentBytes = rpaService.generateRevaluationAct(revaluationData);

            documentStorage.put(documentId, documentBytes);
            documentMetadata.put(documentId, createMetadata("revaluation-act", "generated"));

            log.info("Revaluation act generated successfully via RPA: {}", documentId);
        } catch (Exception e) {
            log.error("Error generating revaluation act via RPA", e);
            documentMetadata.put(documentId, createMetadata("revaluation-act", "stub"));
        }

        return documentId;
    }

    public UUID generateInventoryReport(Map<String, Object> data) {
        log.info("Generating inventory report using RPA");
        log.debug("Inventory report data: {}", data);

        UUID documentId = UUID.randomUUID();

        try {
            InventoryListData inventoryData = mapToInventoryListData(data);

            byte[] documentBytes = rpaService.generateInventoryList(inventoryData);

            documentStorage.put(documentId, documentBytes);
            documentMetadata.put(documentId, createMetadata("inventory-report", "generated"));

            log.info("Inventory report generated successfully via RPA: {}", documentId);
        } catch (Exception e) {
            log.error("Error generating inventory report via RPA", e);
            documentMetadata.put(documentId, createMetadata("inventory-report", "stub"));
        }

        return documentId;
    }

    public UUID generateWriteOffAct(Map<String, Object> data) {
        log.info("Generating write-off act using RPA");
        log.debug("Write-off act data: {}", data);

        UUID documentId = UUID.randomUUID();

        try {
            WriteOffActData writeOffData = mapToWriteOffActData(data);

            byte[] documentBytes = rpaService.generateWriteOffAct(writeOffData);

            documentStorage.put(documentId, documentBytes);
            documentMetadata.put(documentId, createMetadata("write-off-act", "generated"));

            log.info("Write-off act generated successfully via RPA: {}", documentId);
        } catch (Exception e) {
            log.error("Error generating write-off act via RPA", e);
            documentMetadata.put(documentId, createMetadata("write-off-act", "stub"));
        }

        return documentId;
    }

    public UUID generateShipmentOrder(Map<String, Object> data) {
        log.info("Generating shipment order (STUB)");
        UUID documentId = UUID.randomUUID();
        documentMetadata.put(documentId, createMetadata("shipment-order", "stub"));
        return documentId;
    }

    public UUID generateWaybill(Map<String, Object> data) {
        log.info("Generating waybill (shipping invoice) via RPA");
        log.debug("Waybill data: {}", data);

        UUID documentId = UUID.randomUUID();

        try {
            ShippingInvoiceData shippingData = mapToShippingInvoiceData(data);

            byte[] documentBytes = rpaService.generateShippingInvoice(shippingData);

            documentStorage.put(documentId, documentBytes);
            documentMetadata.put(documentId, createMetadata("shipping-invoice", "generated"));

            log.info("Shipping invoice generated successfully via RPA: {}", documentId);
        } catch (Exception e) {
            log.error("Error generating shipping invoice via RPA", e);
            documentMetadata.put(documentId, createMetadata("shipping-invoice", "stub"));
        }

        return documentId;
    }

    public UUID generatePickingList(Map<String, Object> data) {
        log.info("Generating picking list (STUB)");
        UUID documentId = UUID.randomUUID();
        documentMetadata.put(documentId, createMetadata("picking-list", "stub"));
        return documentId;
    }

    public Map<String, Object> getAllDocuments(int page, int size) {
        log.info("Getting all documents: page={}, size={}", page, size);
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> documents = new ArrayList<>();

        documentMetadata.forEach(
                (id, metadata) -> {
                    Map<String, Object> doc = new HashMap<>(metadata);
                    doc.put("id", id.toString());
                    documents.add(doc);
                });

        int start = page * size;
        int end = Math.min(start + size, documents.size());
        List<Map<String, Object>> pageDocuments =
                start < documents.size() ? documents.subList(start, end) : new ArrayList<>();

        result.put("documents", pageDocuments);
        result.put("page", page);
        result.put("size", size);
        result.put("total", documents.size());

        return result;
    }

    public byte[] getDocument(UUID documentId) {
        byte[] document = documentStorage.get(documentId);
        if (document == null) {
            throw new RuntimeException("Document not found: " + documentId);
        }
        return document;
    }

    public Map<String, Object> getDocumentMetadata(UUID documentId) {
        Map<String, Object> metadata = documentMetadata.get(documentId);
        if (metadata == null) {
            throw new RuntimeException("Document metadata not found: " + documentId);
        }
        return metadata;
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
                .commissionMembers(
                        (List<String>) data.getOrDefault("commissionMembers", new ArrayList<>()))
                .items(new ArrayList<>())
                .totalOldValue(BigDecimal.ZERO)
                .totalNewValue(BigDecimal.ZERO)
                .totalDifference(BigDecimal.ZERO)
                .build();
    }

    private InventoryListData mapToInventoryListData(Map<String, Object> data) {
        return InventoryListData.builder()
                .documentNumber(getString(data, "documentNumber", "ИО-001"))
                .documentDate(getDate(data, "documentDate", LocalDate.now()))
                .inventoryDate(getDate(data, "inventoryDate", LocalDate.now()))
                .organizationName(getString(data, "organizationName", "ООО Компания"))
                .inn(getString(data, "inn", "1234567890"))
                .warehouseName(getString(data, "warehouseName", "Склад №1"))
                .chairmanName(getString(data, "chairmanName", "Председатель комиссии"))
                .commissionMembers(
                        (List<String>) data.getOrDefault("commissionMembers", new ArrayList<>()))
                .responsiblePerson(
                        getString(data, "responsiblePerson", "Материально ответственное лицо"))
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
                .shipperAddress(getString(data, "shipperAddress", "г. Минск, ул. Складская, 1"))
                .shipperPhone(getString(data, "shipperPhone", "+375 29 123-45-67"))
                .shipperUnp(getString(data, "shipperUnp", "123456789"))
                .consigneeName(getString(data, "consigneeName", "ООО Грузополучатель"))
                .consigneeAddress(
                        getString(data, "consigneeAddress", "г. Минск, ул. Получателя, 2"))
                .consigneePhone(getString(data, "consigneePhone", "+375 29 987-65-43"))
                .consigneeUnp(getString(data, "consigneeUnp", "987654321"))
                .carrierName(getString(data, "carrierName", "ООО Перевозчик"))
                .carrierVehicle(getString(data, "carrierVehicle", "МАЗ 1234 AB-5"))
                .driverName(getString(data, "driverName", "Иванов Иван Иванович"))
                .driverLicense(getString(data, "driverLicense", "AA 1234567"))
                .loadingPoint(getString(data, "loadingPoint", "г. Минск, ул. Складская, 1"))
                .unloadingPoint(getString(data, "unloadingPoint", "г. Минск, ул. Получателя, 2"))
                .shippingDate(getDate(data, "shippingDate", LocalDate.now()))
                .items(new ArrayList<>())
                .totalQuantity(0)
                .totalWeight(0.0)
                .totalVolume(0.0)
                .totalCost(0.0)
                .releasedBy(getString(data, "releasedBy", "Заведующий складом"))
                .releasedByPosition(getString(data, "releasedByPosition", "Заведующий"))
                .shippedBy(getString(data, "shippedBy", "Кладовщик"))
                .shippedByPosition(getString(data, "shippedByPosition", "Кладовщик"))
                .receivedBy(getString(data, "receivedBy", ""))
                .receivedByPosition(getString(data, "receivedByPosition", ""))
                .notes(getString(data, "notes", ""))
                .specialConditions(getString(data, "specialConditions", ""))
                .build();
    }

    private Map<String, Object> createMetadata(String type, String status) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", type);
        metadata.put("status", status);
        metadata.put("generatedAt", LocalDateTime.now().toString());
        metadata.put("format", type.contains("write-off") ? "docx" : "xls");
        return metadata;
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
