package by.bsuir.documentservice.rpa;

import by.bsuir.documentservice.dto.InventoryListData;
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
