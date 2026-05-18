package by.bsuir.documentservice.rpa;

import by.bsuir.documentservice.dto.InventoryListData;
import by.bsuir.documentservice.dto.CmrData;
import by.bsuir.documentservice.dto.InvoiceData;
import by.bsuir.documentservice.dto.ReceiptActData;
import by.bsuir.documentservice.dto.TransportNoteData;
import by.bsuir.documentservice.dto.ReceiptOrderData;
import by.bsuir.documentservice.dto.RevaluationActData;
import by.bsuir.documentservice.dto.ShippingInvoiceData;
import by.bsuir.documentservice.dto.WriteOffActData;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DocumentRpaService {

    private static final String TEMPLATES_PATH = "document-service/documents template/";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public byte[] generateReceiptOrder(ReceiptOrderData data) {
        log.info("RPA: Generating Receipt Order from template");

        try (InputStream template = loadTemplate("Приходной ордер.XLS");
                HSSFWorkbook workbook = new HSSFWorkbook(template)) {

            Sheet sheet = workbook.getSheetAt(0);

            setCellValue(sheet, 2, 1, data.getDocumentNumber());
            setCellValue(sheet, 2, 5, formatDate(data.getDocumentDate()));
            setCellValue(sheet, 4, 1, data.getOrganizationName());
            setCellValue(sheet, 4, 5, data.getInn());
            setCellValue(sheet, 5, 1, data.getWarehouseName());

            setCellValue(sheet, 7, 1, data.getSupplierName());
            setCellValue(sheet, 7, 5, data.getSupplierInn());

            int rowNum = 10;
            for (ReceiptOrderData.ReceiptItem item : data.getItems()) {
                Row row = getOrCreateRow(sheet, rowNum);

                setCellValue(row, 0, item.getRowNumber());
                setCellValue(row, 1, item.getProductName());
                setCellValue(row, 2, item.getSku());
                setCellValue(row, 3, item.getUnit());
                setCellValue(row, 4, item.getQuantity());
                setCellValue(row, 5, item.getPrice());
                setCellValue(row, 6, item.getAmount());
                setCellValue(row, 7, item.getBatchNumber());

                rowNum++;
            }

            int totalRow = rowNum + 1;
            setCellValue(sheet, totalRow, 4, "ИТОГО:");
            setCellValue(sheet, totalRow, 4, data.getTotalQuantity());
            setCellValue(sheet, totalRow, 6, data.getTotalAmount());

            int signRow = totalRow + 3;
            setCellValue(sheet, signRow, 1, "Принял: " + data.getReceivedBy());
            setCellValue(sheet, signRow + 1, 1, "Утвердил: " + data.getAcceptedBy());

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            log.info("RPA: Receipt Order generated successfully");
            return outputStream.toByteArray();

        } catch (IOException e) {
            log.error("RPA: Error generating Receipt Order", e);
            throw new RuntimeException("Failed to generate receipt order", e);
        }
    }

    public byte[] generateRevaluationAct(RevaluationActData data) {
        log.info("RPA: Generating Revaluation Act from template");

        try (InputStream template = loadTemplate("акт переоценки.xls");
                HSSFWorkbook workbook = new HSSFWorkbook(template)) {

            Sheet sheet = workbook.getSheetAt(0);

            setCellValue(sheet, 2, 1, "Акт переоценки №" + data.getDocumentNumber());
            setCellValue(sheet, 3, 1, "от " + formatDate(data.getDocumentDate()));
            setCellValue(sheet, 5, 1, data.getOrganizationName());
            setCellValue(sheet, 6, 1, "ИНН: " + data.getInn());

            setCellValue(sheet, 8, 1, "Причина: " + data.getReason());
            setCellValue(sheet, 9, 1, data.getReasonDescription());

            setCellValue(sheet, 11, 1, "Председатель комиссии: " + data.getChairmanName());
            int memberRow = 12;
            for (String member : data.getCommissionMembers()) {
                setCellValue(sheet, memberRow++, 1, "Член комиссии: " + member);
            }

            int startRow = memberRow + 2;
            int rowNum = startRow;

            for (RevaluationActData.RevaluationItem item : data.getItems()) {
                Row row = getOrCreateRow(sheet, rowNum);

                setCellValue(row, 0, item.getRowNumber());
                setCellValue(row, 1, item.getProductName());
                setCellValue(row, 2, item.getSku());
                setCellValue(row, 3, item.getUnit());
                setCellValue(row, 4, item.getQuantity());
                setCellValue(row, 5, item.getOldPrice());
                setCellValue(row, 6, item.getNewPrice());
                setCellValue(row, 7, item.getOldValue());
                setCellValue(row, 8, item.getNewValue());
                setCellValue(row, 9, item.getDifference());

                rowNum++;
            }

            int totalRow = rowNum + 1;
            setCellValue(sheet, totalRow, 4, "ИТОГО:");
            setCellValue(sheet, totalRow, 7, data.getTotalOldValue());
            setCellValue(sheet, totalRow, 8, data.getTotalNewValue());
            setCellValue(sheet, totalRow, 9, data.getTotalDifference());

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            log.info("RPA: Revaluation Act generated successfully");
            return outputStream.toByteArray();

        } catch (IOException e) {
            log.error("RPA: Error generating Revaluation Act", e);
            throw new RuntimeException("Failed to generate revaluation act", e);
        }
    }

    public byte[] generateInventoryList(InventoryListData data) {
        log.info("RPA: Generating Inventory List from template");

        try (InputStream template = loadTemplate("инвентарихационная опись.xls");
                HSSFWorkbook workbook = new HSSFWorkbook(template)) {

            Sheet sheet = workbook.getSheetAt(0);

            setCellValue(sheet, 2, 1, "Инвентаризационная опись №" + data.getDocumentNumber());
            setCellValue(sheet, 3, 1, "от " + formatDate(data.getDocumentDate()));
            setCellValue(
                    sheet, 4, 1, "Дата инвентаризации: " + formatDate(data.getInventoryDate()));
            setCellValue(sheet, 6, 1, data.getOrganizationName());
            setCellValue(sheet, 7, 1, "Склад: " + data.getWarehouseName());

            setCellValue(sheet, 9, 1, "Председатель: " + data.getChairmanName());
            int memberRow = 10;
            for (String member : data.getCommissionMembers()) {
                setCellValue(sheet, memberRow++, 1, "Член: " + member);
            }

            setCellValue(
                    sheet,
                    memberRow + 1,
                    1,
                    "Материально ответственное лицо: " + data.getResponsiblePerson());

            int startRow = memberRow + 4;
            int rowNum = startRow;

            for (InventoryListData.InventoryItem item : data.getItems()) {
                Row row = getOrCreateRow(sheet, rowNum);

                setCellValue(row, 0, item.getRowNumber());
                setCellValue(row, 1, item.getProductName());
                setCellValue(row, 2, item.getSku());
                setCellValue(row, 3, item.getUnit());
                setCellValue(row, 4, item.getBookQuantity());
                setCellValue(row, 5, item.getActualQuantity());
                setCellValue(row, 6, item.getDifference());
                setCellValue(row, 7, item.getPrice());
                setCellValue(row, 8, item.getBookValue());
                setCellValue(row, 9, item.getActualValue());
                setCellValue(row, 10, item.getDifferenceValue());
                setCellValue(row, 11, item.getNotes());

                rowNum++;
            }

            int totalRow = rowNum + 1;
            setCellValue(sheet, totalRow, 3, "ИТОГО:");
            setCellValue(sheet, totalRow, 8, data.getTotalBookValue());
            setCellValue(sheet, totalRow, 9, data.getTotalActualValue());
            setCellValue(sheet, totalRow, 10, data.getTotalDifference());

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            log.info("RPA: Inventory List generated successfully");
            return outputStream.toByteArray();

        } catch (IOException e) {
            log.error("RPA: Error generating Inventory List", e);
            throw new RuntimeException("Failed to generate inventory list", e);
        }
    }

    public byte[] generateWriteOffAct(WriteOffActData data) {
        log.info("RPA: Generating Write-Off Act from template");

        try (InputStream template = loadTemplate("списание.docx");
                XWPFDocument document = new XWPFDocument(template)) {

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                replacePlaceholder(paragraph, "{{DOCUMENT_NUMBER}}", data.getDocumentNumber());
                replacePlaceholder(
                        paragraph, "{{DOCUMENT_DATE}}", formatDate(data.getDocumentDate()));
                replacePlaceholder(paragraph, "{{ORGANIZATION_NAME}}", data.getOrganizationName());
                replacePlaceholder(paragraph, "{{INN}}", data.getInn());
                replacePlaceholder(paragraph, "{{REASON}}", data.getReason());
                replacePlaceholder(
                        paragraph, "{{REASON_DESCRIPTION}}", data.getReasonDescription());
                replacePlaceholder(paragraph, "{{DOCUMENT_BASIS}}", data.getDocumentBasis());
                replacePlaceholder(paragraph, "{{CHAIRMAN}}", data.getChairmanName());
                replacePlaceholder(paragraph, "{{RESPONSIBLE}}", data.getResponsiblePerson());
            }

            if (!document.getTables().isEmpty()) {
                XWPFTable table = document.getTables().get(0);

                int rowIndex = 1;
                for (WriteOffActData.WriteOffItem item : data.getItems()) {
                    XWPFTableRow row;
                    if (rowIndex < table.getRows().size()) {
                        row = table.getRow(rowIndex);
                    } else {
                        row = table.createRow();
                    }

                    setCellText(row, 0, item.getRowNumber().toString());
                    setCellText(row, 1, item.getProductName());
                    setCellText(row, 2, item.getSku());
                    setCellText(row, 3, item.getUnit());
                    setCellText(row, 4, item.getQuantity().toString());
                    setCellText(row, 5, formatMoney(item.getPrice()));
                    setCellText(row, 6, formatMoney(item.getValue()));
                    setCellText(row, 7, item.getCondition());

                    rowIndex++;
                }
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.write(outputStream);

            log.info("RPA: Write-Off Act generated successfully");
            return outputStream.toByteArray();

        } catch (IOException e) {
            log.error("RPA: Error generating Write-Off Act", e);
            throw new RuntimeException("Failed to generate write-off act", e);
        }
    }

    public byte[] generateShippingInvoice(ShippingInvoiceData data) {
        log.info("RPA: Generating Shipping Invoice (TTN) from template");

        try (InputStream template = loadTemplate("ттнls.xls");
                HSSFWorkbook workbook = new HSSFWorkbook(template)) {

            Sheet sheet = workbook.getSheetAt(0);

            setCellValue(sheet, 2, 1, "ТОВАРНО-ТРАНСПОРТНАЯ НАКЛАДНАЯ №" + data.getInvoiceNumber());
            setCellValue(sheet, 3, 1, "от " + formatDate(data.getInvoiceDate()));

            setCellValue(sheet, 5, 0, "Грузоотправитель:");
            setCellValue(sheet, 5, 2, data.getShipperName());
            setCellValue(sheet, 6, 2, data.getShipperAddress());
            setCellValue(sheet, 7, 2, "Тел: " + data.getShipperPhone());
            setCellValue(sheet, 8, 2, "УНП: " + data.getShipperUnp());

            setCellValue(sheet, 10, 0, "Грузополучатель:");
            setCellValue(sheet, 10, 2, data.getConsigneeName());
            setCellValue(sheet, 11, 2, data.getConsigneeAddress());
            setCellValue(sheet, 12, 2, "Тел: " + data.getConsigneePhone());
            setCellValue(sheet, 13, 2, "УНП: " + data.getConsigneeUnp());

            setCellValue(sheet, 15, 0, "Перевозчик:");
            setCellValue(sheet, 15, 2, data.getCarrierName());
            setCellValue(sheet, 16, 2, "ТС: " + data.getCarrierVehicle());
            setCellValue(sheet, 17, 2, "Водитель: " + data.getDriverName());
            setCellValue(sheet, 18, 2, "Вод. удост.: " + data.getDriverLicense());

            setCellValue(sheet, 20, 0, "Пункт погрузки:");
            setCellValue(sheet, 20, 2, data.getLoadingPoint());
            setCellValue(sheet, 21, 0, "Пункт разгрузки:");
            setCellValue(sheet, 21, 2, data.getUnloadingPoint());
            setCellValue(sheet, 22, 0, "Дата отгрузки:");
            setCellValue(sheet, 22, 2, formatDate(data.getShippingDate()));

            int rowNum = 25;
            int totalQty = 0;
            double totalWeight = 0.0;
            double totalVolume = 0.0;
            double totalCost = 0.0;

            for (ShippingInvoiceData.ShipmentItem item : data.getItems()) {
                Row row = getOrCreateRow(sheet, rowNum);

                setCellValue(row, 0, item.getPosition());
                setCellValue(row, 1, item.getProductName());
                setCellValue(row, 2, item.getProductCode());
                setCellValue(row, 3, item.getUnit());
                setCellValue(row, 4, item.getQuantity());
                setCellValue(row, 5, item.getWeight());
                setCellValue(row, 6, item.getVolume());
                setCellValue(row, 7, item.getPrice());
                setCellValue(row, 8, item.getTotalPrice());
                setCellValue(row, 9, item.getPackagingType());
                setCellValue(row, 10, item.getNotes());

                totalQty += item.getQuantity();
                totalWeight += item.getWeight() != null ? item.getWeight() : 0.0;
                totalVolume += item.getVolume() != null ? item.getVolume() : 0.0;
                totalCost += item.getTotalPrice() != null ? item.getTotalPrice() : 0.0;

                rowNum++;
            }

            int totalRow = rowNum + 1;
            setCellValue(sheet, totalRow, 3, "ИТОГО:");
            setCellValue(sheet, totalRow, 4, totalQty);
            setCellValue(sheet, totalRow, 5, String.format("%.2f кг", totalWeight));
            setCellValue(sheet, totalRow, 6, String.format("%.2f м³", totalVolume));
            setCellValue(sheet, totalRow, 8, String.format("%.2f руб.", totalCost));

            int signRow = totalRow + 3;
            setCellValue(sheet, signRow, 0, "Груз к перевозке принял:");
            setCellValue(sheet, signRow, 3, data.getReleasedBy());
            setCellValue(sheet, signRow, 6, data.getReleasedByPosition());
            setCellValue(sheet, signRow, 9, "__________");

            setCellValue(sheet, signRow + 2, 0, "Груз принял к перевозке:");
            setCellValue(sheet, signRow + 2, 3, data.getShippedBy());
            setCellValue(sheet, signRow + 2, 6, data.getShippedByPosition());
            setCellValue(sheet, signRow + 2, 9, "__________");

            setCellValue(sheet, signRow + 4, 0, "Груз получил:");
            setCellValue(sheet, signRow + 4, 3, data.getReceivedBy());
            setCellValue(sheet, signRow + 4, 6, data.getReceivedByPosition());
            setCellValue(sheet, signRow + 4, 9, "__________");

            if (data.getNotes() != null && !data.getNotes().isEmpty()) {
                setCellValue(sheet, signRow + 6, 0, "Примечания:");
                setCellValue(sheet, signRow + 6, 2, data.getNotes());
            }

            if (data.getSpecialConditions() != null && !data.getSpecialConditions().isEmpty()) {
                setCellValue(sheet, signRow + 7, 0, "Особые условия:");
                setCellValue(sheet, signRow + 7, 2, data.getSpecialConditions());
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            log.info("RPA: Shipping Invoice (TTN) generated successfully");
            return outputStream.toByteArray();

        } catch (IOException e) {
            log.error("RPA: Error generating Shipping Invoice", e);
            throw new RuntimeException("Failed to generate shipping invoice", e);
        }
    }

    public byte[] generateReceiptAct(ReceiptActData data) {
        log.info("RPA: Generating Receipt Act (discrepancies={})",
                data.hasDiscrepancies() ? data.getDiscrepancies().size() : 0);

        String templateName = data.hasDiscrepancies()
                ? "Акт расхождения.xls"
                : "Акт приемки.RTF";

        if (templateName.endsWith(".xls")) {
            return generateReceiptActXls(data, templateName);
        }
        return generateReceiptActRtf(data, templateName);
    }

    private byte[] generateReceiptActXls(ReceiptActData data, String templateName) {
        try (InputStream template = loadTemplate(templateName);
                HSSFWorkbook workbook = new HSSFWorkbook(template)) {

            Sheet sheet = workbook.getSheetAt(0);

            setCellValue(sheet, 2, 1, "АКТ О РАСХОЖДЕНИИ №" + safe(data.getDocumentNumber()));
            setCellValue(sheet, 3, 1, "от " + formatDate(data.getDocumentDate()));

            setCellValue(sheet, 5, 0, "Организация:");
            setCellValue(sheet, 5, 2, safe(data.getOrganizationName()));
            setCellValue(sheet, 6, 2, "ИНН/УНП: " + safe(data.getInn()));
            setCellValue(sheet, 7, 2, "Склад: " + safe(data.getWarehouseName()));

            setCellValue(sheet, 9, 0, "Поставщик:");
            setCellValue(sheet, 9, 2, safe(data.getSupplierName()));
            setCellValue(sheet, 10, 2, "ИНН/УНП: " + safe(data.getSupplierInn()));

            setCellValue(sheet, 12, 0, "Договор:");
            setCellValue(sheet, 12, 2,
                    safe(data.getContractNumber()) + " от " + safe(data.getContractDate()));
            setCellValue(sheet, 13, 0, "ТТН/ТН:");
            setCellValue(sheet, 13, 2,
                    safe(data.getWaybillNumber()) + " от " + safe(data.getWaybillDate()));

            int rowNum = 16;
            setCellValue(sheet, rowNum, 0, "№");
            setCellValue(sheet, rowNum, 1, "Товар");
            setCellValue(sheet, rowNum, 2, "SKU");
            setCellValue(sheet, rowNum, 3, "Ед.");
            setCellValue(sheet, rowNum, 4, "По док.");
            setCellValue(sheet, rowNum, 5, "Факт");
            setCellValue(sheet, rowNum, 6, "Разница");
            setCellValue(sheet, rowNum, 7, "Тип");
            setCellValue(sheet, rowNum, 8, "Описание дефекта");
            rowNum++;

            if (data.getDiscrepancies() != null) {
                for (ReceiptActData.DiscrepancyItem item : data.getDiscrepancies()) {
                    Row row = getOrCreateRow(sheet, rowNum);
                    setCellValue(row, 0, item.getRowNumber());
                    setCellValue(row, 1, safe(item.getProductName()));
                    setCellValue(row, 2, safe(item.getSku()));
                    setCellValue(row, 3, safe(item.getUnit()));
                    setCellValue(row, 4, item.getExpectedQty());
                    setCellValue(row, 5, item.getActualQty());
                    setCellValue(row, 6, item.getDifferenceQty());
                    setCellValue(row, 7, safe(item.getDiscrepancyType()));
                    setCellValue(row, 8, safe(item.getDefectDescription()));
                    rowNum++;
                }
            }

            int notesRow = rowNum + 2;
            setCellValue(sheet, notesRow, 0, "Заключение комиссии:");
            setCellValue(sheet, notesRow, 1, safe(data.getGeneralNotes()));

            int signRow = notesRow + 3;
            setCellValue(sheet, signRow, 0, "Председатель: " + safe(data.getChairmanName()));
            int memberRow = signRow + 1;
            if (data.getCommissionMembers() != null) {
                for (String member : data.getCommissionMembers()) {
                    setCellValue(sheet, memberRow++, 0, "Член комиссии: " + member);
                }
            }
            setCellValue(sheet, memberRow + 1, 0, "Принял: " + safe(data.getAcceptedBy()));
            setCellValue(sheet, memberRow + 2, 0, "Утвердил: " + safe(data.getApprovedBy()));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("RPA: Error generating Receipt Act XLS", e);
            throw new RuntimeException("Failed to generate receipt act", e);
        }
    }

    private byte[] generateReceiptActRtf(ReceiptActData data, String templateName) {
        try (InputStream template = loadTemplate(templateName)) {
            String rtf = new String(template.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

            String filled = rtf
                    .replace("{{documentNumber}}", safe(data.getDocumentNumber()))
                    .replace("{{documentDate}}", formatDate(data.getDocumentDate()))
                    .replace("{{organizationName}}", safe(data.getOrganizationName()))
                    .replace("{{inn}}", safe(data.getInn()))
                    .replace("{{warehouseName}}", safe(data.getWarehouseName()))
                    .replace("{{supplierName}}", safe(data.getSupplierName()))
                    .replace("{{supplierInn}}", safe(data.getSupplierInn()))
                    .replace("{{contractNumber}}", safe(data.getContractNumber()))
                    .replace("{{waybillNumber}}", safe(data.getWaybillNumber()))
                    .replace("{{chairmanName}}", safe(data.getChairmanName()))
                    .replace("{{acceptedBy}}", safe(data.getAcceptedBy()))
                    .replace("{{approvedBy}}", safe(data.getApprovedBy()));

            return filled.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("RPA: Error generating Receipt Act RTF", e);
            throw new RuntimeException("Failed to generate receipt act", e);
        }
    }

    private String safe(String value) {
        return value != null ? value : "—";
    }

    public byte[] generateInvoice(InvoiceData data) {
        log.info("RPA: Generating Invoice (currency={})", data.getCurrency());

        try (InputStream template = loadTemplate("blank-invojs.doc");
                org.apache.poi.hwpf.HWPFDocument doc = new org.apache.poi.hwpf.HWPFDocument(template)) {

            org.apache.poi.hwpf.usermodel.Range range = doc.getRange();
            String currency = data.getCurrency() != null ? data.getCurrency() : "BYN";

            replaceInDoc(range, "{{documentNumber}}", safe(data.getDocumentNumber()));
            replaceInDoc(range, "{{documentDate}}", formatDate(data.getDocumentDate()));
            replaceInDoc(range, "{{currency}}", currency);
            replaceInDoc(range, "{{sellerName}}", safe(data.getSellerName()));
            replaceInDoc(range, "{{sellerInn}}", safe(data.getSellerInn()));
            replaceInDoc(range, "{{sellerAddress}}", safe(data.getSellerAddress()));
            replaceInDoc(range, "{{buyerName}}", safe(data.getBuyerName()));
            replaceInDoc(range, "{{buyerInn}}", safe(data.getBuyerInn()));
            replaceInDoc(range, "{{buyerAddress}}", safe(data.getBuyerAddress()));
            replaceInDoc(range, "{{contractNumber}}", safe(data.getContractNumber()));
            replaceInDoc(range, "{{contractDate}}", safe(data.getContractDate()));
            replaceInDoc(range, "{{totalAmount}}",
                    data.getTotalAmount() != null ? data.getTotalAmount().toPlainString() : "0.00");
            replaceInDoc(range, "{{totalAmountInWords}}", safe(data.getTotalAmountInWords()));
            replaceInDoc(range, "{{vatRate}}",
                    data.getVatRate() != null ? data.getVatRate().toPlainString() : "0");
            replaceInDoc(range, "{{vatAmount}}",
                    data.getVatAmount() != null ? data.getVatAmount().toPlainString() : "0.00");
            replaceInDoc(range, "{{responsiblePerson}}", safe(data.getResponsiblePerson()));
            replaceInDoc(range, "{{notes}}", safe(data.getNotes()));

            replaceInDoc(range, "{{itemsTable}}", buildItemsBlock(data));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("RPA: Error generating Invoice", e);
            throw new RuntimeException("Failed to generate invoice", e);
        }
    }

    private void replaceInDoc(org.apache.poi.hwpf.usermodel.Range range, String token, String value) {
        try {
            range.replaceText(token, value != null ? value : "");
        } catch (Exception ignored) {
        }
    }

    public byte[] generateCmr(CmrData data) {
        log.info("RPA: Generating CMR (currency={}, country {} → {})",
                data.getCurrency(), data.getShipperCountry(), data.getConsigneeCountry());

        try (InputStream template = loadTemplate("CMR Международная товарно-транспортная накладная.doc");
                org.apache.poi.hwpf.HWPFDocument doc = new org.apache.poi.hwpf.HWPFDocument(template)) {

            org.apache.poi.hwpf.usermodel.Range range = doc.getRange();
            String currency = data.getCurrency() != null ? data.getCurrency() : "EUR";

            replaceInDoc(range, "{{documentNumber}}", safe(data.getDocumentNumber()));
            replaceInDoc(range, "{{documentDate}}", formatDate(data.getDocumentDate()));
            replaceInDoc(range, "{{currency}}", currency);

            replaceInDoc(range, "{{shipperName}}", safe(data.getShipperName()));
            replaceInDoc(range, "{{shipperInn}}", safe(data.getShipperInn()));
            replaceInDoc(range, "{{shipperAddress}}", safe(data.getShipperAddress()));
            replaceInDoc(range, "{{shipperCountry}}", safe(data.getShipperCountry()));
            replaceInDoc(range, "{{shipperGln}}", safe(data.getShipperGln()));

            replaceInDoc(range, "{{consigneeName}}", safe(data.getConsigneeName()));
            replaceInDoc(range, "{{consigneeInn}}", safe(data.getConsigneeInn()));
            replaceInDoc(range, "{{consigneeAddress}}", safe(data.getConsigneeAddress()));
            replaceInDoc(range, "{{consigneeCountry}}", safe(data.getConsigneeCountry()));
            replaceInDoc(range, "{{consigneeGln}}", safe(data.getConsigneeGln()));

            replaceInDoc(range, "{{carrierName}}", safe(data.getCarrierName()));
            replaceInDoc(range, "{{carrierAddress}}", safe(data.getCarrierAddress()));
            replaceInDoc(range, "{{vehicleNumber}}", safe(data.getVehicleNumber()));
            replaceInDoc(range, "{{trailerNumber}}", safe(data.getTrailerNumber()));
            replaceInDoc(range, "{{driverName}}", safe(data.getDriverName()));
            replaceInDoc(range, "{{driverPassport}}", safe(data.getDriverPassport()));

            replaceInDoc(range, "{{placeOfLoading}}", safe(data.getPlaceOfLoading()));
            replaceInDoc(range, "{{placeOfDelivery}}", safe(data.getPlaceOfDelivery()));
            replaceInDoc(range, "{{loadingDate}}", formatDate(data.getLoadingDate()));
            replaceInDoc(range, "{{deliveryDate}}", formatDate(data.getDeliveryDate()));

            replaceInDoc(range, "{{totalQuantity}}",
                    data.getTotalQuantity() != null ? data.getTotalQuantity().toPlainString() : "0");
            replaceInDoc(range, "{{totalWeightKg}}",
                    data.getTotalWeight() != null ? data.getTotalWeight().toPlainString() : "0");
            replaceInDoc(range, "{{totalVolumeM3}}",
                    data.getTotalVolume() != null ? data.getTotalVolume().toPlainString() : "0");
            replaceInDoc(range, "{{cargoDeclaredValue}}",
                    data.getCargoDeclaredValue() != null
                            ? data.getCargoDeclaredValue().toPlainString() + " " + currency
                            : "0 " + currency);

            replaceInDoc(range, "{{paymentInstructions}}", safe(data.getPaymentInstructions()));
            replaceInDoc(range, "{{specialInstructions}}", safe(data.getSpecialInstructions()));
            replaceInDoc(range, "{{contractNumber}}", safe(data.getContractNumber()));

            replaceInDoc(range, "{{shipperSignedBy}}", safe(data.getShipperSignedBy()));
            replaceInDoc(range, "{{carrierSignedBy}}", safe(data.getCarrierSignedBy()));
            replaceInDoc(range, "{{consigneeSignedBy}}", safe(data.getConsigneeSignedBy()));

            replaceInDoc(range, "{{itemsTable}}", buildCmrItemsBlock(data, currency));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("RPA: Error generating CMR", e);
            throw new RuntimeException("Failed to generate CMR", e);
        }
    }

    private String buildCmrItemsBlock(CmrData data, String currency) {
        if (data.getItems() == null || data.getItems().isEmpty()) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        for (CmrData.CmrItem item : data.getItems()) {
            sb.append(item.getRowNumber() != null ? item.getRowNumber() : "?")
                    .append(". ")
                    .append(safe(item.getProductName()))
                    .append(" [HS ").append(safe(item.getHsCode())).append("]")
                    .append(" — упак.: ").append(safe(item.getPackagingType()))
                    .append(", марк.: ").append(safe(item.getMarks()))
                    .append(", кол.: ")
                    .append(item.getQuantity() != null ? item.getQuantity().toPlainString() : "0")
                    .append(" ").append(safe(item.getUnit()))
                    .append(", вес: ")
                    .append(item.getGrossWeightKg() != null ? item.getGrossWeightKg().toPlainString() : "0")
                    .append(" кг, объём: ")
                    .append(item.getVolumeM3() != null ? item.getVolumeM3().toPlainString() : "0")
                    .append(" м³, ст-ть: ")
                    .append(item.getDeclaredValue() != null ? item.getDeclaredValue().toPlainString() : "0")
                    .append(" ").append(currency)
                    .append("\n");
        }
        return sb.toString();
    }

    public byte[] generateTransportNote(TransportNoteData data) {
        String layout = data.getLayout() != null ? data.getLayout().toLowerCase() : "horizontal";
        String templateName = "vertical".equals(layout) ? "tn-vert.xls" : "tn-gor.xls";
        log.info("RPA: Generating Transport Note (layout={}, template={})", layout, templateName);

        try (InputStream template = loadTemplate(templateName);
                HSSFWorkbook workbook = new HSSFWorkbook(template)) {

            Sheet sheet = workbook.getSheetAt(0);
            String currency = data.getCurrency() != null ? data.getCurrency() : "BYN";

            setCellValue(sheet, 2, 1,
                    "ТОВАРНАЯ НАКЛАДНАЯ №" + safe(data.getDocumentNumber())
                            + " от " + formatDate(data.getDocumentDate()));

            setCellValue(sheet, 4, 0, "Грузоотправитель:");
            setCellValue(sheet, 4, 2, safe(data.getShipperName()));
            setCellValue(sheet, 5, 2, "ИНН/УНП: " + safe(data.getShipperInn()));
            setCellValue(sheet, 6, 2, safe(data.getShipperAddress()));

            setCellValue(sheet, 8, 0, "Грузополучатель:");
            setCellValue(sheet, 8, 2, safe(data.getConsigneeName()));
            setCellValue(sheet, 9, 2, "ИНН/УНП: " + safe(data.getConsigneeInn()));
            setCellValue(sheet, 10, 2, safe(data.getConsigneeAddress()));

            setCellValue(sheet, 12, 0, "Склад отгрузки:");
            setCellValue(sheet, 12, 2, safe(data.getWarehouseName()));
            setCellValue(sheet, 13, 0, "Договор:");
            setCellValue(sheet, 13, 2,
                    safe(data.getContractNumber()) + " от " + safe(data.getContractDate()));
            setCellValue(sheet, 14, 0, "Валюта:");
            setCellValue(sheet, 14, 2, currency);

            int headerRow = 16;
            setCellValue(sheet, headerRow, 0, "№");
            setCellValue(sheet, headerRow, 1, "Товар");
            setCellValue(sheet, headerRow, 2, "SKU");
            setCellValue(sheet, headerRow, 3, "Ед.");
            setCellValue(sheet, headerRow, 4, "Кол-во");
            setCellValue(sheet, headerRow, 5, "Цена, " + currency);
            setCellValue(sheet, headerRow, 6, "Сумма, " + currency);
            setCellValue(sheet, headerRow, 7, "НДС %");
            setCellValue(sheet, headerRow, 8, "НДС, " + currency);

            int rowNum = headerRow + 1;
            if (data.getItems() != null) {
                for (TransportNoteData.TransportItem item : data.getItems()) {
                    Row row = getOrCreateRow(sheet, rowNum);
                    setCellValue(row, 0, item.getRowNumber());
                    setCellValue(row, 1, safe(item.getProductName()));
                    setCellValue(row, 2, safe(item.getSku()));
                    setCellValue(row, 3, safe(item.getUnit()));
                    setCellValue(row, 4, item.getQuantity());
                    setCellValue(row, 5, item.getUnitPrice());
                    setCellValue(row, 6, item.getTotalPrice());
                    setCellValue(row, 7, item.getVatRate());
                    setCellValue(row, 8, item.getVatAmount());
                    rowNum++;
                }
            }

            int totalsRow = rowNum + 1;
            setCellValue(sheet, totalsRow, 0, "ИТОГО:");
            setCellValue(sheet, totalsRow, 4, data.getTotalQuantity());
            setCellValue(sheet, totalsRow, 6, data.getTotalAmount());
            setCellValue(sheet, totalsRow, 8, data.getTotalVat());

            int waybillRow = totalsRow + 2;
            setCellValue(sheet, waybillRow, 0, "Товар к доставке принял:");
            setCellValue(sheet, waybillRow, 2, safe(data.getWaybillReference()));

            int signRow = waybillRow + 2;
            setCellValue(sheet, signRow, 0, "Отпустил: " + safe(data.getReleasedBy()));
            setCellValue(sheet, signRow + 1, 0, "Принял: " + safe(data.getAcceptedBy()));

            if (data.getNotes() != null && !data.getNotes().isBlank()) {
                setCellValue(sheet, signRow + 3, 0, "Примечание: " + data.getNotes());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("RPA: Error generating Transport Note", e);
            throw new RuntimeException("Failed to generate transport note", e);
        }
    }

    private String buildItemsBlock(InvoiceData data) {
        if (data.getItems() == null || data.getItems().isEmpty()) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        for (InvoiceData.InvoiceItem item : data.getItems()) {
            sb.append(item.getRowNumber() != null ? item.getRowNumber() : "?")
                    .append(". ")
                    .append(safe(item.getProductName()))
                    .append(" (").append(safe(item.getSku())).append(") — ")
                    .append(item.getQuantity() != null ? item.getQuantity().toPlainString() : "0")
                    .append(" ").append(safe(item.getUnit()))
                    .append(" × ")
                    .append(item.getUnitPrice() != null ? item.getUnitPrice().toPlainString() : "0.00")
                    .append(" = ")
                    .append(item.getTotalPrice() != null ? item.getTotalPrice().toPlainString() : "0.00")
                    .append("\n");
        }
        return sb.toString();
    }

    private InputStream loadTemplate(String filename) throws IOException {
        Path templatePath = Paths.get(TEMPLATES_PATH + filename);
        if (Files.exists(templatePath)) {
            return Files.newInputStream(templatePath);
        }

        return new ClassPathResource("templates/" + filename).getInputStream();
    }

    private void setCellValue(Sheet sheet, int rowNum, int colNum, Object value) {
        Row row = getOrCreateRow(sheet, rowNum);
        setCellValue(row, colNum, value);
    }

    private void setCellValue(Row row, int colNum, Object value) {
        Cell cell = getOrCreateCell(row, colNum);

        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof String) {
            cell.setCellValue((String) value);
        } else if (value instanceof Integer) {
            cell.setCellValue((Integer) value);
        } else if (value instanceof BigDecimal) {
            cell.setCellValue(((BigDecimal) value).doubleValue());
        } else if (value instanceof Double) {
            cell.setCellValue((Double) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private Row getOrCreateRow(Sheet sheet, int rowNum) {
        Row row = sheet.getRow(rowNum);
        if (row == null) {
            row = sheet.createRow(rowNum);
        }
        return row;
    }

    private Cell getOrCreateCell(Row row, int colNum) {
        Cell cell = row.getCell(colNum);
        if (cell == null) {
            cell = row.createCell(colNum);
        }
        return cell;
    }

    private void replacePlaceholder(XWPFParagraph paragraph, String placeholder, String value) {
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.getText(0);
            if (text != null && text.contains(placeholder)) {
                text = text.replace(placeholder, value != null ? value : "");
                run.setText(text, 0);
            }
        }
    }

    private void setCellText(XWPFTableRow row, int cellIndex, String text) {
        if (cellIndex < row.getTableCells().size()) {
            row.getCell(cellIndex).setText(text != null ? text : "");
        }
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER) : "";
    }

    private String formatMoney(BigDecimal amount) {
        return amount != null ? String.format("%.2f", amount) : "0.00";
    }
}
