package by.bsuir.documentservice.rpa;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PdfDocumentService {

    private static final float MARGIN = 50f;
    private static final float LINE_HEIGHT = 16f;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public byte[] generateReceiptOrderPdf(Map<String, Object> data) {
        return buildPdf("ПРИХОДНЫЙ ОРДЕР", List.of(
                row("Дата", getStr(data, "date", today())),
                row("Поставщик", getStr(data, "supplierName", "—")),
                row("Склад", getStr(data, "warehouseName", "—")),
                row("Товар", getStr(data, "productName", "—")),
                row("Количество", getStr(data, "quantity", "0")),
                row("Цена за единицу", getStr(data, "unitPrice", "0.00")),
                row("Сумма", getStr(data, "totalAmount", "0.00")),
                row("Партия", getStr(data, "batchNumber", "—")),
                row("Примечание", getStr(data, "notes", ""))
        ));
    }

    public byte[] generateShippingInvoicePdf(Map<String, Object> data) {
        return buildPdf("ТОВАРНО-ТРАНСПОРТНАЯ НАКЛАДНАЯ", List.of(
                row("Дата", getStr(data, "date", today())),
                row("Получатель", getStr(data, "recipientName", "—")),
                row("Адрес получателя", getStr(data, "recipientAddress", "—")),
                row("Склад отгрузки", getStr(data, "warehouseName", "—")),
                row("Товар", getStr(data, "productName", "—")),
                row("Количество", getStr(data, "quantity", "0")),
                row("Цена", getStr(data, "unitPrice", "0.00")),
                row("Сумма", getStr(data, "totalAmount", "0.00")),
                row("Примечание", getStr(data, "notes", ""))
        ));
    }

    public byte[] generateWriteOffActPdf(Map<String, Object> data) {
        return buildPdf("АКТ СПИСАНИЯ ТОВАРНО-МАТЕРИАЛЬНЫХ ЦЕННОСТЕЙ", List.of(
                row("Дата", getStr(data, "date", today())),
                row("Организация", getStr(data, "organizationName", "—")),
                row("Склад", getStr(data, "warehouseName", "—")),
                row("Товар", getStr(data, "productName", "—")),
                row("Количество", getStr(data, "quantity", "0")),
                row("Причина списания", getStr(data, "reason", "—")),
                row("Ответственный", getStr(data, "responsiblePerson", "—")),
                row("Примечание", getStr(data, "notes", ""))
        ));
    }

    public byte[] generateRevaluationActPdf(Map<String, Object> data) {
        return buildPdf("АКТ ПЕРЕОЦЕНКИ ТОВАРНО-МАТЕРИАЛЬНЫХ ЦЕННОСТЕЙ", List.of(
                row("Дата", getStr(data, "date", today())),
                row("Организация", getStr(data, "organizationName", "—")),
                row("Склад", getStr(data, "warehouseName", "—")),
                row("Товар", getStr(data, "productName", "—")),
                row("Старая цена", getStr(data, "oldPrice", "0.00")),
                row("Новая цена", getStr(data, "newPrice", "0.00")),
                row("Количество", getStr(data, "quantity", "0")),
                row("Разница", getStr(data, "priceDifference", "0.00")),
                row("Ответственный", getStr(data, "responsiblePerson", "—"))
        ));
    }

    public byte[] generateInventoryListPdf(Map<String, Object> data) {
        return buildPdf("ИНВЕНТАРИЗАЦИОННАЯ ОПИСЬ", List.of(
                row("Дата", getStr(data, "date", today())),
                row("Склад", getStr(data, "warehouseName", "—")),
                row("Организация", getStr(data, "organizationName", "—")),
                row("Период инвентаризации", getStr(data, "period", "—")),
                row("Ответственный", getStr(data, "responsiblePerson", "—")),
                row("Итого позиций", getStr(data, "totalItems", "0")),
                row("Расхождений", getStr(data, "discrepancyCount", "0"))
        ));
    }

    public byte[] generatePickingListPdf(Map<String, Object> data) {
        return buildPdf("СБОРОЧНЫЙ ЛИСТ (ЛИСТ ПОДБОРА)", List.of(
                row("Дата", getStr(data, "date", today())),
                row("Заказ / Отгрузка", getStr(data, "shipmentId", "—")),
                row("Склад", getStr(data, "warehouseName", "—")),
                row("Исполнитель", getStr(data, "assignedTo", "—")),
                row("Товар", getStr(data, "productName", "—")),
                row("Ячейка", getStr(data, "cellAddress", "—")),
                row("Количество к подбору", getStr(data, "quantityToPick", "0")),
                row("Партия (срок годности)", getStr(data, "batchExpiry", "—")),
                row("Примечание", getStr(data, "notes", ""))
        ));
    }

    public byte[] generateReleaseOrderPdf(Map<String, Object> data) {
        return buildPdf("ОТПУСКНОЙ ОРДЕР", List.of(
                row("Номер", getStr(data, "documentNumber", "—")),
                row("Дата", getStr(data, "date", today())),
                row("Получатель", getStr(data, "recipientName", "—")),
                row("Склад", getStr(data, "warehouseName", "—")),
                row("Заявка", getStr(data, "shipmentRequestId", "—")),
                row("Товар", getStr(data, "productName", "—")),
                row("Партия", getStr(data, "batchNumber", "—")),
                row("Количество", getStr(data, "quantity", "0")),
                row("Цена", getStr(data, "unitPrice", "0.00")),
                row("Сумма", getStr(data, "totalAmount", "0.00")),
                row("Кладовщик", getStr(data, "releasedBy", "—")),
                row("Примечание", getStr(data, "notes", ""))
        ));
    }

    public byte[] generateReceiptActPdf(Map<String, Object> data) {
        return buildPdf("АКТ ПРИЁМКИ ТОВАРА", List.of(
                row("Номер", getStr(data, "documentNumber", "—")),
                row("Дата", getStr(data, "date", today())),
                row("Поставщик", getStr(data, "supplierName", "—")),
                row("Склад", getStr(data, "warehouseName", "—")),
                row("Поставка", getStr(data, "supplyId", "—")),
                row("Товар", getStr(data, "productName", "—")),
                row("Партия", getStr(data, "batchNumber", "—")),
                row("Заявленное количество", getStr(data, "expectedQty", "0")),
                row("Фактическое количество", getStr(data, "actualQty", "0")),
                row("Расхождение", getStr(data, "discrepancy", "0")),
                row("Условия хранения", getStr(data, "storageConditions", "—")),
                row("Принял", getStr(data, "receivedBy", "—")),
                row("Председатель комиссии", getStr(data, "chairmanName", "—")),
                row("Состав комиссии", getStr(data, "commissionMembers", "—")),
                row("Примечание", getStr(data, "notes", ""))
        ));
    }

    public byte[] generateInvoiceFactPdf(Map<String, Object> data) {
        return buildPdf("СЧЁТ-ФАКТУРА", List.of(
                row("Номер", getStr(data, "invoiceNumber", "—")),
                row("Дата", getStr(data, "date", today())),
                row("Поставщик", getStr(data, "supplierName", "—")),
                row("УНП поставщика", getStr(data, "supplierInn", "—")),
                row("Покупатель", getStr(data, "buyerName", "—")),
                row("УНП покупателя", getStr(data, "buyerInn", "—")),
                row("Договор", getStr(data, "contractNumber", "—")),
                row("Товар", getStr(data, "productName", "—")),
                row("Количество", getStr(data, "quantity", "0")),
                row("Цена без НДС", getStr(data, "unitPrice", "0.00")),
                row("Ставка НДС", getStr(data, "vatRate", "20%")),
                row("Сумма НДС", getStr(data, "vatAmount", "0.00")),
                row("Итого с НДС", getStr(data, "totalAmount", "0.00")),
                row("Подписал", getStr(data, "signedBy", "—"))
        ));
    }

    public byte[] generateInvoicePdf(Map<String, Object> data) {
        return buildPdf("ИНВОЙС (INVOICE)", List.of(
                row("Invoice number", getStr(data, "invoiceNumber", "—")),
                row("Date", getStr(data, "date", today())),
                row("Seller", getStr(data, "sellerName", "—")),
                row("Seller address", getStr(data, "sellerAddress", "—")),
                row("Buyer", getStr(data, "buyerName", "—")),
                row("Buyer address", getStr(data, "buyerAddress", "—")),
                row("Goods description", getStr(data, "productName", "—")),
                row("Quantity", getStr(data, "quantity", "0")),
                row("Unit price", getStr(data, "unitPrice", "0.00")),
                row("Currency", getStr(data, "currency", "USD")),
                row("Total", getStr(data, "totalAmount", "0.00")),
                row("Payment terms", getStr(data, "paymentTerms", "—")),
                row("Notes", getStr(data, "notes", ""))
        ));
    }

    public byte[] generateTransportNotePdf(Map<String, Object> data) {
        return buildPdf("ТОВАРНАЯ НАКЛАДНАЯ (ТН)", List.of(
                row("Номер", getStr(data, "documentNumber", "—")),
                row("Дата", getStr(data, "date", today())),
                row("Грузоотправитель", getStr(data, "shipperName", "—")),
                row("Адрес отправителя", getStr(data, "shipperAddress", "—")),
                row("Грузополучатель", getStr(data, "consigneeName", "—")),
                row("Адрес получателя", getStr(data, "consigneeAddress", "—")),
                row("Товар", getStr(data, "productName", "—")),
                row("Количество", getStr(data, "quantity", "0")),
                row("Единица измерения", getStr(data, "unit", "шт")),
                row("Цена", getStr(data, "unitPrice", "0.00")),
                row("Сумма", getStr(data, "totalAmount", "0.00")),
                row("Отпустил", getStr(data, "releasedBy", "—")),
                row("Принял", getStr(data, "receivedBy", "—"))
        ));
    }

    public byte[] generateCmrPdf(Map<String, Object> data) {
        return buildPdf("CMR (МЕЖДУНАРОДНАЯ ТРАНСПОРТНАЯ НАКЛАДНАЯ)", List.of(
                row("CMR number", getStr(data, "cmrNumber", "—")),
                row("Date", getStr(data, "date", today())),
                row("Sender", getStr(data, "senderName", "—")),
                row("Sender country", getStr(data, "senderCountry", "—")),
                row("Consignee", getStr(data, "consigneeName", "—")),
                row("Consignee country", getStr(data, "consigneeCountry", "—")),
                row("Place of taking over", getStr(data, "loadingPlace", "—")),
                row("Place of delivery", getStr(data, "deliveryPlace", "—")),
                row("Carrier", getStr(data, "carrierName", "—")),
                row("Vehicle number", getStr(data, "vehicleNumber", "—")),
                row("Driver", getStr(data, "driverName", "—")),
                row("Goods", getStr(data, "productName", "—")),
                row("Gross weight (kg)", getStr(data, "grossWeight", "0")),
                row("Number of packages", getStr(data, "packageCount", "0")),
                row("Sender's instructions", getStr(data, "senderInstructions", "—")),
                row("Special agreements", getStr(data, "specialAgreements", "—"))
        ));
    }

    public byte[] generateDiscrepancyActPdf(Map<String, Object> data) {
        return buildPdf("АКТ О РАСХОЖДЕНИИ", List.of(
                row("Номер", getStr(data, "documentNumber", "—")),
                row("Дата", getStr(data, "date", today())),
                row("Организация", getStr(data, "organizationName", "—")),
                row("Склад", getStr(data, "warehouseName", "—")),
                row("Поставка / Инвентаризация", getStr(data, "sourceReference", "—")),
                row("Товар", getStr(data, "productName", "—")),
                row("Партия", getStr(data, "batchNumber", "—")),
                row("Ожидалось", getStr(data, "expectedQuantity", "0")),
                row("Фактически", getStr(data, "actualQuantity", "0")),
                row("Расхождение", getStr(data, "discrepancy", "0")),
                row("Причина", getStr(data, "reason", "—")),
                row("Председатель комиссии", getStr(data, "chairmanName", "—")),
                row("Состав комиссии", getStr(data, "commissionMembers", "—")),
                row("Решение", getStr(data, "decision", "—"))
        ));
    }

    private byte[] buildPdf(String title, List<String[]> rows) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDFont fontRegular = loadFont(doc, "fonts/DejaVuSans.ttf");
            PDFont fontBold = loadFont(doc, "fonts/DejaVuSans-Bold.ttf");

            float pageWidth = page.getMediaBox().getWidth();
            float startY = page.getMediaBox().getHeight() - MARGIN;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = startY;

                cs.beginText();
                cs.setFont(fontBold, 14);
                cs.newLineAtOffset(MARGIN, y);
                cs.showText(title);
                cs.endText();
                y -= LINE_HEIGHT * 2;

                cs.beginText();
                cs.setFont(fontRegular, 10);
                cs.setLeading(LINE_HEIGHT);
                cs.newLineAtOffset(MARGIN, y);

                for (String[] row : rows) {
                    String line = row[0] + ": " + row[1];
                    if (line.length() > 90) {
                        line = line.substring(0, 90) + "...";
                    }
                    cs.showText(line);
                    cs.newLine();
                    y -= LINE_HEIGHT;
                    if (y < MARGIN + LINE_HEIGHT) break;
                }

                y -= LINE_HEIGHT * 2;
                cs.setFont(fontRegular, 9);
                cs.showText("Документ сформирован системой WMS  " + today());
                cs.endText();

                cs.moveTo(MARGIN, y + LINE_HEIGHT);
                cs.lineTo(pageWidth - MARGIN, y + LINE_HEIGHT);
                cs.stroke();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Ошибка генерации PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось сгенерировать PDF: " + e.getMessage(), e);
        }
    }

    private PDFont loadFont(PDDocument doc, String classpath) throws java.io.IOException {
        try (InputStream is = new ClassPathResource(classpath).getInputStream()) {
            return PDType0Font.load(doc, is, true);
        }
    }

    private String[] row(String label, String value) {
        return new String[]{label, value};
    }

    private String getStr(Map<String, Object> data, String key, String defaultVal) {
        Object v = data.get(key);
        return v != null ? v.toString() : defaultVal;
    }

    private String today() {
        return LocalDate.now().format(DATE_FMT);
    }
}