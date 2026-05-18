package by.bsuir.documentservice.rpa;

import by.bsuir.documentservice.config.RpaProperties;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RpaTemplateBinding {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final RpaProperties props;

    public record Binding(String templateName, Map<String, String> cells, Map<String, String> placeholders) {
        public boolean isWord() {
            String lower = templateName.toLowerCase();
            return lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".rtf");
        }
    }

    public Binding bind(String type, Map<String, Object> data) {
        try {
            return switch (type) {
                case "receipt-order" -> bindReceiptOrder(data);
                case "revaluation-act" -> bindRevaluationAct(data);
                case "inventory-report" -> bindInventoryReport(data);
                case "write-off-act" -> bindWriteOffAct(data);
                case "waybill" -> bindWaybill(data);
                case "receipt-act" -> bindReceiptAct(data);
                case "invoice" -> bindInvoice(data);
                case "transport-note" -> bindTransportNote(data);
                case "cmr" -> bindCmr(data);
                default -> null;
            };
        } catch (Exception e) {
            log.warn("RpaTemplateBinding failed for type={}: {}", type, e.getMessage());
            return null;
        }
    }

    private Binding bindReceiptOrder(Map<String, Object> data) {
        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("B3", asStrOr(data.get("documentNumber"), "ПО-001"));
        cells.put("F3", asStrOr(data.get("documentDate"), today()));
        cells.put("B5", asStr(data.get("organizationName")));
        cells.put("F5", asStr(data.get("inn")));
        cells.put("B6", asStr(data.get("warehouseName")));
        cells.put("B8", asStr(data.get("supplierName")));
        cells.put("F8", asStr(data.get("supplierInn")));
        addItemsRows(data, cells, 11,
                List.of("productName", "sku", "unit", "quantity", "price", "amount", "batchNumber"));
        return new Binding(props.getTemplates().getReceiptOrder(), cells, Map.of());
    }

    private Binding bindRevaluationAct(Map<String, Object> data) {
        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("B3", "Акт переоценки №" + asStr(data.get("documentNumber")));
        cells.put("B4", "от " + asStrOr(data.get("documentDate"), today()));
        cells.put("B6", asStr(data.get("organizationName")));
        cells.put("B7", "ИНН: " + asStr(data.get("inn")));
        cells.put("B9", "Причина: " + asStr(data.get("reason")));
        cells.put("B10", asStr(data.get("reasonDescription")));
        cells.put("B12", "Председатель комиссии: " + asStr(data.get("chairmanName")));

        int memberRow = 13;
        List<?> members = asList(data.get("commissionMembers"));
        for (Object m : members) {
            cells.put("B" + memberRow++, "Член комиссии: " + asStr(m));
        }
        int itemsStart = memberRow + 2;
        addItemsRows(data, cells, itemsStart,
                List.of("productName", "sku", "unit", "quantity",
                        "oldPrice", "newPrice", "oldValue", "newValue", "difference"));
        return new Binding(props.getTemplates().getRevaluationAct(), cells, Map.of());
    }

    private Binding bindInventoryReport(Map<String, Object> data) {
        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("B3", "Инвентаризационная опись №" + asStr(data.get("documentNumber")));
        cells.put("B4", "от " + asStrOr(data.get("documentDate"), today()));
        cells.put("B5", "Дата инвентаризации: " + asStrOr(data.get("inventoryDate"), today()));
        cells.put("B7", asStr(data.get("organizationName")));
        cells.put("B8", "Склад: " + asStr(data.get("warehouseName")));
        cells.put("B10", "Председатель: " + asStr(data.get("chairmanName")));

        int memberRow = 11;
        List<?> members = asList(data.get("commissionMembers"));
        for (Object m : members) {
            cells.put("B" + memberRow++, "Член: " + asStr(m));
        }
        cells.put("B" + (memberRow + 1),
                "Материально ответственное лицо: " + asStr(data.get("responsiblePerson")));

        int itemsStart = memberRow + 4;
        addItemsRows(data, cells, itemsStart,
                List.of("productName", "sku", "unit", "bookQuantity", "actualQuantity",
                        "difference", "price", "bookValue", "actualValue", "differenceValue", "notes"));
        return new Binding(props.getTemplates().getInventoryReport(), cells, Map.of());
    }

    private Binding bindWriteOffAct(Map<String, Object> data) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("DOCUMENT_NUMBER", asStr(data.get("documentNumber")));
        p.put("DOCUMENT_DATE", asStrOr(data.get("documentDate"), today()));
        p.put("ORGANIZATION_NAME", asStr(data.get("organizationName")));
        p.put("INN", asStr(data.get("inn")));
        p.put("REASON", asStr(data.get("reason")));
        p.put("REASON_DESCRIPTION", asStr(data.get("reasonDescription")));
        p.put("DOCUMENT_BASIS", asStr(data.get("documentBasis")));
        p.put("CHAIRMAN", asStr(data.get("chairmanName")));
        p.put("RESPONSIBLE", asStr(data.get("responsiblePerson")));
        return new Binding(props.getTemplates().getWriteOffAct(), Map.of(), p);
    }

    private Binding bindWaybill(Map<String, Object> data) {
        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("B3", "ТОВАРНО-ТРАНСПОРТНАЯ НАКЛАДНАЯ №" + asStr(data.get("invoiceNumber")));
        cells.put("B4", "от " + asStrOr(data.get("invoiceDate"), today()));
        cells.put("A6", "Грузоотправитель:");
        cells.put("C6", asStr(data.get("shipperName")));
        cells.put("C7", asStr(data.get("shipperAddress")));
        cells.put("C8", "Тел: " + asStr(data.get("shipperPhone")));
        cells.put("C9", "УНП: " + asStr(data.get("shipperUnp")));
        cells.put("A11", "Грузополучатель:");
        cells.put("C11", asStr(data.get("consigneeName")));
        cells.put("C12", asStr(data.get("consigneeAddress")));
        cells.put("C13", "Тел: " + asStr(data.get("consigneePhone")));
        cells.put("C14", "УНП: " + asStr(data.get("consigneeUnp")));
        cells.put("A16", "Перевозчик:");
        cells.put("C16", asStr(data.get("carrierName")));
        cells.put("C17", "ТС: " + asStr(data.get("carrierVehicle")));
        cells.put("C18", "Водитель: " + asStr(data.get("driverName")));
        cells.put("C19", "Вод. удост.: " + asStr(data.get("driverLicense")));
        cells.put("A21", "Пункт погрузки:");
        cells.put("C21", asStr(data.get("loadingPoint")));
        cells.put("A22", "Пункт разгрузки:");
        cells.put("C22", asStr(data.get("unloadingPoint")));
        cells.put("A23", "Дата отгрузки:");
        cells.put("C23", asStrOr(data.get("shippingDate"), today()));
        addItemsRows(data, cells, 26,
                List.of("productName", "productCode", "unit", "quantity",
                        "weight", "volume", "price", "totalPrice", "packagingType", "notes"));
        return new Binding(props.getTemplates().getWaybill(), cells, Map.of());
    }

    private Binding bindReceiptAct(Map<String, Object> data) {
        List<?> discrepancies = asList(data.get("discrepancies"));
        if (discrepancies.isEmpty()) {
            return null;
        }
        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("B3", "АКТ О РАСХОЖДЕНИИ №" + asStr(data.get("documentNumber")));
        cells.put("B4", "от " + asStrOr(data.get("documentDate"), today()));
        cells.put("A6", "Организация:");
        cells.put("C6", asStr(data.get("organizationName")));
        cells.put("C7", "ИНН/УНП: " + asStr(data.get("inn")));
        cells.put("C8", "Склад: " + asStr(data.get("warehouseName")));
        cells.put("A10", "Поставщик:");
        cells.put("C10", asStr(data.get("supplierName")));
        cells.put("C11", "ИНН/УНП: " + asStr(data.get("supplierInn")));
        cells.put("A13", "Договор:");
        cells.put("C13", asStr(data.get("contractNumber")) + " от " + asStr(data.get("contractDate")));
        cells.put("A14", "ТТН/ТН:");
        cells.put("C14", asStr(data.get("waybillNumber")) + " от " + asStr(data.get("waybillDate")));

        int rowNum = 18;
        int idx = 1;
        for (Object raw : discrepancies) {
            if (!(raw instanceof Map<?, ?> m)) continue;
            cells.put("A" + rowNum, String.valueOf(idx++));
            cells.put("B" + rowNum, asStr(m.get("productName")));
            cells.put("C" + rowNum, asStr(m.get("sku")));
            cells.put("D" + rowNum, asStrOr(m.get("unit"), "шт"));
            cells.put("E" + rowNum, asStr(m.get("expectedQty")));
            cells.put("F" + rowNum, asStr(m.get("actualQty")));
            cells.put("G" + rowNum, asStr(m.get("differenceQty")));
            cells.put("H" + rowNum, asStr(m.get("discrepancyType")));
            cells.put("I" + rowNum, asStr(m.get("defectDescription")));
            rowNum++;
        }
        int notesRow = rowNum + 2;
        cells.put("A" + notesRow, "Заключение комиссии:");
        cells.put("B" + notesRow, asStr(data.get("generalNotes")));

        int signRow = notesRow + 3;
        cells.put("A" + signRow, "Председатель: " + asStr(data.get("chairmanName")));
        int memberRow = signRow + 1;
        List<?> members = asList(data.get("commissionMembers"));
        for (Object m : members) {
            cells.put("A" + memberRow++, "Член комиссии: " + asStr(m));
        }
        cells.put("A" + (memberRow + 1), "Принял: " + asStr(data.get("acceptedBy")));
        cells.put("A" + (memberRow + 2), "Утвердил: " + asStr(data.get("approvedBy")));
        return new Binding(props.getTemplates().getReceiptActDiscrepancy(), cells, Map.of());
    }

    private Binding bindInvoice(Map<String, Object> data) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("documentNumber", asStr(data.get("documentNumber")));
        p.put("documentDate", asStrOr(data.get("documentDate"), today()));
        p.put("currency", asStrOr(data.get("currency"), "BYN"));
        p.put("sellerName", asStr(data.get("sellerName")));
        p.put("sellerInn", asStr(data.get("sellerInn")));
        p.put("sellerAddress", asStr(data.get("sellerAddress")));
        p.put("buyerName", asStr(data.get("buyerName")));
        p.put("buyerInn", asStr(data.get("buyerInn")));
        p.put("buyerAddress", asStr(data.get("buyerAddress")));
        p.put("contractNumber", asStr(data.get("contractNumber")));
        p.put("contractDate", asStr(data.get("contractDate")));
        p.put("totalAmount", asStrOr(data.get("totalAmount"), "0.00"));
        p.put("totalAmountInWords", asStr(data.get("totalAmountInWords")));
        p.put("vatRate", asStrOr(data.get("vatRate"), "0"));
        p.put("vatAmount", asStrOr(data.get("vatAmount"), "0.00"));
        p.put("responsiblePerson", asStr(data.get("responsiblePerson")));
        p.put("notes", asStr(data.get("notes")));
        p.put("itemsTable", buildItemsTextBlock(data,
                List.of("productName", "sku", "quantity", "unit", "unitPrice", "totalPrice")));
        return new Binding(props.getTemplates().getInvoice(), Map.of(), p);
    }

    private Binding bindTransportNote(Map<String, Object> data) {
        String layout = asStrOr(data.get("layout"), "horizontal").toLowerCase();
        String templateName = "vertical".equals(layout)
                ? props.getTemplates().getTransportNoteVertical()
                : props.getTemplates().getTransportNoteHorizontal();
        String currency = asStrOr(data.get("currency"), "BYN");

        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("B3", "ТОВАРНАЯ НАКЛАДНАЯ №" + asStr(data.get("documentNumber"))
                + " от " + asStrOr(data.get("documentDate"), today()));

        cells.put("A5", "Грузоотправитель:");
        cells.put("C5", asStr(data.get("shipperName")));
        cells.put("C6", "ИНН/УНП: " + asStr(data.get("shipperInn")));
        cells.put("C7", asStr(data.get("shipperAddress")));

        cells.put("A9", "Грузополучатель:");
        cells.put("C9", asStr(data.get("consigneeName")));
        cells.put("C10", "ИНН/УНП: " + asStr(data.get("consigneeInn")));
        cells.put("C11", asStr(data.get("consigneeAddress")));

        cells.put("A13", "Склад отгрузки:");
        cells.put("C13", asStr(data.get("warehouseName")));
        cells.put("A14", "Договор:");
        cells.put("C14", asStr(data.get("contractNumber")) + " от " + asStr(data.get("contractDate")));
        cells.put("A15", "Валюта:");
        cells.put("C15", currency);

        cells.put("A17", "№");
        cells.put("B17", "Товар");
        cells.put("C17", "SKU");
        cells.put("D17", "Ед.");
        cells.put("E17", "Кол-во");
        cells.put("F17", "Цена, " + currency);
        cells.put("G17", "Сумма, " + currency);
        cells.put("H17", "НДС %");
        cells.put("I17", "НДС, " + currency);

        addItemsRows(data, cells, 18,
                List.of("productName", "sku", "unit", "quantity",
                        "unitPrice", "totalPrice", "vatRate", "vatAmount"));
        return new Binding(templateName, cells, Map.of());
    }

    private Binding bindCmr(Map<String, Object> data) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("documentNumber", asStr(data.get("documentNumber")));
        p.put("documentDate", asStrOr(data.get("documentDate"), today()));
        p.put("currency", asStrOr(data.get("currency"), "EUR"));

        p.put("shipperName", asStr(data.get("shipperName")));
        p.put("shipperInn", asStr(data.get("shipperInn")));
        p.put("shipperAddress", asStr(data.get("shipperAddress")));
        p.put("shipperCountry", asStr(data.get("shipperCountry")));
        p.put("shipperGln", asStr(data.get("shipperGln")));

        p.put("consigneeName", asStr(data.get("consigneeName")));
        p.put("consigneeInn", asStr(data.get("consigneeInn")));
        p.put("consigneeAddress", asStr(data.get("consigneeAddress")));
        p.put("consigneeCountry", asStr(data.get("consigneeCountry")));
        p.put("consigneeGln", asStr(data.get("consigneeGln")));

        p.put("carrierName", asStr(data.get("carrierName")));
        p.put("carrierAddress", asStr(data.get("carrierAddress")));
        p.put("vehicleNumber", asStr(data.get("vehicleNumber")));
        p.put("trailerNumber", asStr(data.get("trailerNumber")));
        p.put("driverName", asStr(data.get("driverName")));
        p.put("driverPassport", asStr(data.get("driverPassport")));

        p.put("placeOfLoading", asStr(data.get("placeOfLoading")));
        p.put("placeOfDelivery", asStr(data.get("placeOfDelivery")));
        p.put("loadingDate", asStrOr(data.get("loadingDate"), today()));
        p.put("deliveryDate", asStrOr(data.get("deliveryDate"), today()));

        p.put("totalQuantity", asStrOr(data.get("totalQuantity"), "0"));
        p.put("totalWeightKg", asStrOr(data.get("totalWeightKg"), "0"));
        p.put("totalVolumeM3", asStrOr(data.get("totalVolumeM3"), "0"));
        p.put("cargoDeclaredValue", asStrOr(data.get("cargoDeclaredValue"), "0"));

        p.put("paymentInstructions", asStr(data.get("paymentInstructions")));
        p.put("specialInstructions", asStr(data.get("specialInstructions")));
        p.put("contractNumber", asStr(data.get("contractNumber")));

        p.put("shipperSignedBy", asStr(data.get("shipperSignedBy")));
        p.put("carrierSignedBy", asStr(data.get("carrierSignedBy")));
        p.put("consigneeSignedBy", asStr(data.get("consigneeSignedBy")));

        p.put("itemsTable", buildItemsTextBlock(data,
                List.of("productName", "hsCode", "packagingType", "marks",
                        "quantity", "unit", "grossWeightKg", "volumeM3", "declaredValue")));
        return new Binding(props.getTemplates().getCmr(), Map.of(), p);
    }

    private void addItemsRows(
            Map<String, Object> data,
            Map<String, String> cells,
            int startRow,
            List<String> fields) {
        List<?> items = asList(data.get("items"));
        int rowNum = startRow;
        int idx = 1;
        for (Object raw : items) {
            if (!(raw instanceof Map<?, ?> m)) continue;
            cells.put("A" + rowNum, String.valueOf(idx++));
            for (int i = 0; i < fields.size(); i++) {
                char col = (char) ('B' + i);
                cells.put(col + String.valueOf(rowNum), asStr(m.get(fields.get(i))));
            }
            rowNum++;
        }
    }

    private String buildItemsTextBlock(Map<String, Object> data, List<String> fields) {
        List<?> items = asList(data.get("items"));
        if (items.isEmpty()) return "—";
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (Object raw : items) {
            if (!(raw instanceof Map<?, ?> m)) continue;
            sb.append(idx++).append(". ");
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) sb.append(" | ");
                sb.append(asStr(m.get(fields.get(i))));
            }
            sb.append("; ");
        }
        return sb.toString();
    }

    private List<?> asList(Object v) {
        return v instanceof List<?> list ? list : List.of();
    }

    private String asStr(Object v) {
        return v != null ? v.toString() : "";
    }

    private String asStrOr(Object v, String fallback) {
        if (v == null) return fallback;
        String s = v.toString();
        return s.isEmpty() ? fallback : s;
    }

    private String today() {
        return LocalDate.now().format(DATE_FORMATTER);
    }
}
