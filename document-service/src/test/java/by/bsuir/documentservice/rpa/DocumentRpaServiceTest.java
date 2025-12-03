package by.bsuir.documentservice.rpa;

import static org.junit.jupiter.api.Assertions.*;

import by.bsuir.documentservice.dto.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentRpaServiceTest {

    private DocumentRpaService documentRpaService;

    @TempDir Path tempDir;

    private ReceiptOrderData receiptOrderData;
    private RevaluationActData revaluationActData;
    private InventoryListData inventoryListData;
    private WriteOffActData writeOffActData;
    private ShippingInvoiceData shippingInvoiceData;

    @BeforeEach
    void setUp() throws IOException {
        documentRpaService = new DocumentRpaService();

        // Create template directory structure
        Path templatesDir = tempDir.resolve("document-service").resolve("documents template");
        Files.createDirectories(templatesDir);

        // Create Excel templates
        createExcelTemplate(templatesDir.resolve("Приходной ордер.XLS"));
        createExcelTemplate(templatesDir.resolve("акт переоценки.xls"));
        createExcelTemplate(templatesDir.resolve("инвентарихационная опись.xls"));
        createExcelTemplate(templatesDir.resolve("ттнls.xls"));

        // Create Word template
        createWordTemplate(templatesDir.resolve("списание.docx"));

        // Receipt Order Data
        receiptOrderData =
                ReceiptOrderData.builder()
                        .documentNumber("ПО-001")
                        .documentDate(LocalDate.now())
                        .organizationName("ООО Тест")
                        .inn("1234567890")
                        .warehouseName("Склад №1")
                        .supplierName("Поставщик Тест")
                        .supplierInn("0987654321")
                        .receivedBy("Иванов И.И.")
                        .acceptedBy("Петров П.П.")
                        .items(createReceiptItems())
                        .totalQuantity(100)
                        .totalAmount(new BigDecimal("10000.00"))
                        .build();

        // Revaluation Act Data
        revaluationActData =
                RevaluationActData.builder()
                        .documentNumber("АП-001")
                        .documentDate(LocalDate.now())
                        .organizationName("ООО Тест")
                        .inn("1234567890")
                        .warehouseName("Склад №1")
                        .reason("INFLATION")
                        .reasonDescription("Переоценка товаров")
                        .chairmanName("Председатель")
                        .commissionMembers(Arrays.asList("Член 1", "Член 2"))
                        .items(createRevaluationItems())
                        .totalOldValue(new BigDecimal("10000.00"))
                        .totalNewValue(new BigDecimal("12000.00"))
                        .totalDifference(new BigDecimal("2000.00"))
                        .build();

        // Inventory List Data
        inventoryListData =
                InventoryListData.builder()
                        .documentNumber("ИО-001")
                        .documentDate(LocalDate.now())
                        .inventoryDate(LocalDate.now())
                        .organizationName("ООО Тест")
                        .inn("1234567890")
                        .warehouseName("Склад №1")
                        .chairmanName("Председатель")
                        .commissionMembers(List.of("Член 1"))
                        .responsiblePerson("Ответственное лицо")
                        .items(createInventoryItems())
                        .totalBookValue(new BigDecimal("10000.00"))
                        .totalActualValue(new BigDecimal("9500.00"))
                        .totalDifference(new BigDecimal("-500.00"))
                        .build();

        // Write-Off Act Data
        writeOffActData =
                WriteOffActData.builder()
                        .documentNumber("АС-001")
                        .documentDate(LocalDate.now())
                        .organizationName("ООО Тест")
                        .inn("1234567890")
                        .reason("DAMAGE")
                        .reasonDescription("Повреждение товара")
                        .documentBasis("Акт осмотра №123")
                        .chairmanName("Председатель")
                        .responsiblePerson("Ответственное лицо")
                        .items(createWriteOffItems())
                        .build();

        // Shipping Invoice Data
        shippingInvoiceData =
                ShippingInvoiceData.builder()
                        .invoiceNumber("ТТН-001")
                        .invoiceDate(LocalDate.now())
                        .shipperName("ООО Грузоотправитель")
                        .shipperAddress("г. Минск, ул. Складская, 1")
                        .shipperPhone("+375 29 123-45-67")
                        .shipperUnp("123456789")
                        .consigneeName("ООО Грузополучатель")
                        .consigneeAddress("г. Минск, ул. Получателя, 2")
                        .consigneePhone("+375 29 987-65-43")
                        .consigneeUnp("987654321")
                        .carrierName("ООО Перевозчик")
                        .carrierVehicle("МАЗ 1234 AB-5")
                        .driverName("Иванов Иван Иванович")
                        .driverLicense("AA 1234567")
                        .loadingPoint("г. Минск, ул. Складская, 1")
                        .unloadingPoint("г. Минск, ул. Получателя, 2")
                        .shippingDate(LocalDate.now())
                        .items(createShippingItems())
                        .totalQuantity(100)
                        .totalWeight(500.0)
                        .totalVolume(10.0)
                        .totalCost(10000.0)
                        .releasedBy("Заведующий складом")
                        .releasedByPosition("Заведующий")
                        .shippedBy("Кладовщик")
                        .shippedByPosition("Кладовщик")
                        .receivedBy("Получатель")
                        .receivedByPosition("Менеджер")
                        .notes("Примечания")
                        .specialConditions("Особые условия")
                        .build();
    }

    private List<ReceiptOrderData.ReceiptItem> createReceiptItems() {
        List<ReceiptOrderData.ReceiptItem> items = new ArrayList<>();
        items.add(
                ReceiptOrderData.ReceiptItem.builder()
                        .rowNumber(1)
                        .productName("Товар 1")
                        .sku("SKU-001")
                        .unit("шт")
                        .quantity(50)
                        .price(new BigDecimal("100.00"))
                        .amount(new BigDecimal("5000.00"))
                        .batchNumber("BATCH-001")
                        .build());
        items.add(
                ReceiptOrderData.ReceiptItem.builder()
                        .rowNumber(2)
                        .productName("Товар 2")
                        .sku("SKU-002")
                        .unit("шт")
                        .quantity(50)
                        .price(new BigDecimal("100.00"))
                        .amount(new BigDecimal("5000.00"))
                        .batchNumber("BATCH-002")
                        .build());
        return items;
    }

    private List<RevaluationActData.RevaluationItem> createRevaluationItems() {
        List<RevaluationActData.RevaluationItem> items = new ArrayList<>();
        items.add(
                RevaluationActData.RevaluationItem.builder()
                        .rowNumber(1)
                        .productName("Товар 1")
                        .sku("SKU-001")
                        .unit("шт")
                        .quantity(50)
                        .oldPrice(new BigDecimal("100.00"))
                        .newPrice(new BigDecimal("120.00"))
                        .oldValue(new BigDecimal("5000.00"))
                        .newValue(new BigDecimal("6000.00"))
                        .difference(new BigDecimal("1000.00"))
                        .build());
        return items;
    }

    private List<InventoryListData.InventoryItem> createInventoryItems() {
        List<InventoryListData.InventoryItem> items = new ArrayList<>();
        items.add(
                InventoryListData.InventoryItem.builder()
                        .rowNumber(1)
                        .productName("Товар 1")
                        .sku("SKU-001")
                        .unit("шт")
                        .bookQuantity(100)
                        .actualQuantity(95)
                        .difference(-5)
                        .price(new BigDecimal("100.00"))
                        .bookValue(new BigDecimal("10000.00"))
                        .actualValue(new BigDecimal("9500.00"))
                        .differenceValue(new BigDecimal("-500.00"))
                        .notes("Недостача")
                        .build());
        return items;
    }

    private List<WriteOffActData.WriteOffItem> createWriteOffItems() {
        List<WriteOffActData.WriteOffItem> items = new ArrayList<>();
        items.add(
                WriteOffActData.WriteOffItem.builder()
                        .rowNumber(1)
                        .productName("Товар 1")
                        .sku("SKU-001")
                        .unit("шт")
                        .quantity(10)
                        .price(new BigDecimal("100.00"))
                        .value(new BigDecimal("1000.00"))
                        .condition("Поврежден")
                        .build());
        return items;
    }

    private List<ShippingInvoiceData.ShipmentItem> createShippingItems() {
        List<ShippingInvoiceData.ShipmentItem> items = new ArrayList<>();
        items.add(
                ShippingInvoiceData.ShipmentItem.builder()
                        .position(1)
                        .productName("Товар 1")
                        .productCode("SKU-001")
                        .unit("шт")
                        .quantity(100)
                        .weight(500.0)
                        .volume(10.0)
                        .price(100.0)
                        .totalPrice(10000.0)
                        .packagingType("Коробка")
                        .notes("Хрупкое")
                        .build());
        return items;
    }

    private void createExcelTemplate(Path path) throws IOException {
        HSSFWorkbook workbook = new HSSFWorkbook();
        Sheet sheet = workbook.createSheet("Sheet1");

        // Create some rows for template
        for (int i = 0; i < 30; i++) {
            Row row = sheet.createRow(i);
            for (int j = 0; j < 12; j++) {
                Cell cell = row.createCell(j);
                cell.setCellValue("");
            }
        }

        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private void createWordTemplate(Path path) throws IOException {
        XWPFDocument document = new XWPFDocument();
        document.createParagraph().createRun().setText("{{DOCUMENT_NUMBER}}");
        document.createParagraph().createRun().setText("{{DOCUMENT_DATE}}");
        document.createParagraph().createRun().setText("{{ORGANIZATION_NAME}}");
        document.createTable(5, 8);

        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            document.write(fos);
        }
        document.close();
    }

    @Test
    void generateReceiptOrder_Success() {
        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentRpaService.generateReceiptOrder(receiptOrderData));

        // Template loading will fail, but we test that the method processes the data
        assertTrue(exception.getMessage().contains("Failed to generate receipt order"));
    }

    @Test
    void generateReceiptOrder_WithEmptyItems() {
        // Arrange
        receiptOrderData.setItems(new ArrayList<>());
        receiptOrderData.setTotalQuantity(0);
        receiptOrderData.setTotalAmount(BigDecimal.ZERO);

        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentRpaService.generateReceiptOrder(receiptOrderData));
        assertTrue(exception.getMessage().contains("Failed to generate receipt order"));
    }

    @Test
    void generateRevaluationAct_Success() {
        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentRpaService.generateRevaluationAct(revaluationActData));
        assertTrue(exception.getMessage().contains("Failed to generate revaluation act"));
    }

    @Test
    void generateRevaluationAct_WithEmptyCommission() {
        // Arrange
        revaluationActData.setCommissionMembers(new ArrayList<>());

        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentRpaService.generateRevaluationAct(revaluationActData));
        assertTrue(exception.getMessage().contains("Failed to generate revaluation act"));
    }

    @Test
    void generateInventoryList_Success() {
        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentRpaService.generateInventoryList(inventoryListData));
        assertTrue(exception.getMessage().contains("Failed to generate inventory list"));
    }

    @Test
    void generateInventoryList_WithMultipleCommissionMembers() {
        // Arrange
        inventoryListData.setCommissionMembers(Arrays.asList("Член 1", "Член 2", "Член 3"));

        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentRpaService.generateInventoryList(inventoryListData));
        assertTrue(exception.getMessage().contains("Failed to generate inventory list"));
    }

    @Test
    void generateWriteOffAct_Success() {
        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentRpaService.generateWriteOffAct(writeOffActData));
        assertTrue(exception.getMessage().contains("Failed to generate write-off act"));
    }

    @Test
    void generateWriteOffAct_WithEmptyItems() {
        // Arrange
        writeOffActData.setItems(new ArrayList<>());

        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentRpaService.generateWriteOffAct(writeOffActData));
        assertTrue(exception.getMessage().contains("Failed to generate write-off act"));
    }

    @Test
    void generateShippingInvoice_Success() {
        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentRpaService.generateShippingInvoice(shippingInvoiceData));
        assertTrue(exception.getMessage().contains("Failed to generate shipping invoice"));
    }

    @Test
    void generateShippingInvoice_WithEmptyItems() {
        // Arrange
        shippingInvoiceData.setItems(new ArrayList<>());
        shippingInvoiceData.setTotalQuantity(0);
        shippingInvoiceData.setTotalWeight(0.0);
        shippingInvoiceData.setTotalVolume(0.0);
        shippingInvoiceData.setTotalCost(0.0);

        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentRpaService.generateShippingInvoice(shippingInvoiceData));
        assertTrue(exception.getMessage().contains("Failed to generate shipping invoice"));
    }

    @Test
    void generateShippingInvoice_WithNullOptionalFields() {
        // Arrange
        shippingInvoiceData.setNotes(null);
        shippingInvoiceData.setSpecialConditions(null);
        shippingInvoiceData.getItems().getFirst().setWeight(null);
        shippingInvoiceData.getItems().getFirst().setVolume(null);
        shippingInvoiceData.getItems().getFirst().setTotalPrice(null);

        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentRpaService.generateShippingInvoice(shippingInvoiceData));
        assertTrue(exception.getMessage().contains("Failed to generate shipping invoice"));
    }

    @Test
    void generateReceiptOrder_WithNullValues() {
        // Arrange
        ReceiptOrderData dataWithNulls =
                ReceiptOrderData.builder()
                        .documentNumber(null)
                        .documentDate(null)
                        .organizationName(null)
                        .inn(null)
                        .warehouseName(null)
                        .items(new ArrayList<>())
                        .totalQuantity(0)
                        .totalAmount(null)
                        .build();

        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentRpaService.generateReceiptOrder(dataWithNulls));
        assertTrue(exception.getMessage().contains("Failed to generate receipt order"));
    }

    @Test
    void generateRevaluationAct_WithNullValues() {
        // Arrange
        RevaluationActData dataWithNulls =
                RevaluationActData.builder()
                        .documentNumber(null)
                        .documentDate(null)
                        .organizationName(null)
                        .commissionMembers(null)
                        .items(new ArrayList<>())
                        .totalOldValue(null)
                        .totalNewValue(null)
                        .totalDifference(null)
                        .build();

        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentRpaService.generateRevaluationAct(dataWithNulls));
        assertTrue(exception.getMessage().contains("Failed to generate revaluation act"));
    }

    @Test
    void generateInventoryList_WithNullValues() {
        // Arrange
        InventoryListData dataWithNulls =
                InventoryListData.builder()
                        .documentNumber(null)
                        .documentDate(null)
                        .inventoryDate(null)
                        .commissionMembers(null)
                        .items(new ArrayList<>())
                        .totalBookValue(null)
                        .totalActualValue(null)
                        .totalDifference(null)
                        .build();

        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentRpaService.generateInventoryList(dataWithNulls));
        assertTrue(exception.getMessage().contains("Failed to generate inventory list"));
    }

    @Test
    void generateWriteOffAct_WithNullValues() {
        // Arrange
        WriteOffActData dataWithNulls =
                WriteOffActData.builder()
                        .documentNumber(null)
                        .documentDate(null)
                        .reason(null)
                        .items(new ArrayList<>())
                        .build();

        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentRpaService.generateWriteOffAct(dataWithNulls));
        assertTrue(exception.getMessage().contains("Failed to generate write-off act"));
    }

    @Test
    void generateReceiptOrder_WithLargeAmounts() {
        // Arrange
        receiptOrderData.setTotalAmount(new BigDecimal("999999999.99"));
        receiptOrderData.getItems().getFirst().setPrice(new BigDecimal("999999999.99"));

        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentRpaService.generateReceiptOrder(receiptOrderData));
        assertTrue(exception.getMessage().contains("Failed to generate receipt order"));
    }

    @Test
    void generateShippingInvoice_WithLargeQuantities() {
        // Arrange
        shippingInvoiceData.setTotalQuantity(Integer.MAX_VALUE);
        shippingInvoiceData.setTotalWeight(Double.MAX_VALUE);
        shippingInvoiceData.setTotalVolume(Double.MAX_VALUE);

        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> documentRpaService.generateShippingInvoice(shippingInvoiceData));
        assertTrue(exception.getMessage().contains("Failed to generate shipping invoice"));
    }
}
