package by.bsuir.productservice.service;

import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductBatch;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductBatchRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductJourneyService {

    private final ProductReadModelRepository productRepository;
    private final ProductBatchRepository batchRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductOperationRepository operationRepository;
    private final by.bsuir.productservice.client.WarehouseClient warehouseClient;

    @Transactional(readOnly = true)
    public Map<String, Object> getJourney(UUID productId, UUID batchId, UUID inventoryId, UUID organizationId) {
        log.info("Building journey for product={} batch={} inventory={} (org={})",
                productId, batchId, inventoryId, organizationId);

        ProductReadModel product = productRepository.findById(productId)
                .orElseThrow(() -> AppException.notFound("Товар не найден"));
        if (organizationId != null && product.getOrganizationId() != null
                && !organizationId.equals(product.getOrganizationId())) {
            throw AppException.forbidden("Товар принадлежит другой организации");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("productId", productId);
        result.put("productName", product.getName());
        result.put("sku", product.getSku());
        result.put("category", product.getCategory());
        result.put("abcClass", product.getAbcClass());

        List<ProductBatch> batches;
        if (batchId != null) {
            batches = batchRepository.findById(batchId).map(List::of).orElse(List.of());
        } else if (organizationId != null) {
            batches = batchRepository.findByOrganizationIdAndProductId(organizationId, productId);
        } else {
            batches = batchRepository.findByProductId(productId);
        }
        result.put("batches", batches.stream().map(this::batchInfo).collect(Collectors.toList()));

        List<Inventory> stocks;
        if (inventoryId != null) {
            stocks = inventoryRepository.findById(inventoryId).map(List::of).orElse(List.of());
        } else if (organizationId != null) {
            stocks = inventoryRepository.findByOrganizationIdAndProductId(organizationId, productId);
        } else {
            stocks = inventoryRepository.findByProductId(productId);
        }
        if (batchId != null) {
            stocks = stocks.stream().filter(s -> batchId.equals(s.getBatchId())).collect(Collectors.toList());
        }
        result.put("currentStocks", stocks.stream().map(this::stockInfo).collect(Collectors.toList()));

        List<ProductOperation> operations = (organizationId != null)
                ? operationRepository.findByOrganizationIdAndProductIdOrderByOperationDateDesc(organizationId, productId)
                : operationRepository.findByProductIdOrderByOperationDateDesc(productId);
        if (batchId != null) {
            operations = operations.stream()
                    .filter(o -> batchId.equals(o.getBatchId()))
                    .collect(Collectors.toList());
        }
        result.put("operations", operations.stream().map(this::operationInfo).collect(Collectors.toList()));

        return result;
    }

    private static final String[] OP_HEADERS = { "Дата и время", "Операция", "Склад", "Кол-во" };
    private static final float[] OP_COL_WIDTHS = { 140f, 130f, 160f, 85f };

    public byte[] generateJourneyPdf(UUID productId, UUID batchId, UUID inventoryId, UUID organizationId) {
        Map<String, Object> journey = getJourney(productId, batchId, inventoryId, organizationId);

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDFont regular;
            PDFont bold;
            try (InputStream regStream = new ClassPathResource("fonts/DejaVuSans.ttf").getInputStream();
                 InputStream boldStream = new ClassPathResource("fonts/DejaVuSans-Bold.ttf").getInputStream()) {
                regular = PDType0Font.load(doc, regStream, true);
                bold = PDType0Font.load(doc, boldStream, true);
            }

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);

            float marginLeft = 40f;
            float y = page.getMediaBox().getHeight() - 60f;

            cs.beginText();
            cs.setFont(bold, 14);
            cs.newLineAtOffset(marginLeft, y);
            cs.showText("Карточка товара: " + String.valueOf(journey.get("productName")));
            cs.endText();
            y -= 22;

            cs.beginText();
            cs.setFont(regular, 10);
            cs.newLineAtOffset(marginLeft, y);
            cs.showText("ID товара: " + productId);
            cs.newLineAtOffset(0, -14);
            cs.showText("Артикул (SKU): " + String.valueOf(journey.get("sku")));
            cs.newLineAtOffset(0, -14);
            cs.showText("ABC-класс: " + String.valueOf(journey.get("abcClass")));
            cs.endText();
            y -= 56;

            cs.beginText();
            cs.setFont(bold, 12);
            cs.newLineAtOffset(marginLeft, y);
            cs.showText("История операций");
            cs.endText();
            y -= 16;

            drawTableHeader(cs, bold, marginLeft, y);
            y -= 18;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> operations = (List<Map<String, Object>>) journey.get("operations");
            Map<String, String> warehouseNameCache = new HashMap<>();
            for (Map<String, Object> op : operations) {
                if (y < 60) {
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = page.getMediaBox().getHeight() - 60f;
                    drawTableHeader(cs, bold, marginLeft, y);
                    y -= 18;
                }
                String[] cells = new String[] {
                        formatDate(op.get("operationDate")),
                        translateOperation(String.valueOf(op.get("operationType"))),
                        warehouseLabel(op.get("warehouseId"), warehouseNameCache),
                        formatQty(op.get("quantity"))
                };
                drawTableRow(cs, regular, marginLeft, y, cells);
                y -= 16;
            }

            cs.close();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate journey PDF: {}", e.getMessage(), e);
            throw AppException.internalError("Ошибка генерации PDF карточки товара: " + e.getMessage());
        }
    }

    private void drawTableHeader(PDPageContentStream cs, PDFont bold, float x, float y) throws java.io.IOException {
        float totalWidth = 0f;
        for (float w : OP_COL_WIDTHS) totalWidth += w;
        cs.setLineWidth(0.5f);
        cs.addRect(x, y - 2, totalWidth, 16);
        cs.stroke();
        drawCells(cs, bold, 10, x, y + 2, OP_HEADERS);
    }

    private void drawTableRow(PDPageContentStream cs, PDFont regular, float x, float y, String[] cells)
            throws java.io.IOException {
        float totalWidth = 0f;
        for (float w : OP_COL_WIDTHS) totalWidth += w;
        cs.setLineWidth(0.25f);
        cs.addRect(x, y - 2, totalWidth, 14);
        cs.stroke();
        drawCells(cs, regular, 9, x, y + 2, cells);
    }

    private void drawCells(PDPageContentStream cs, PDFont font, float fontSize, float x, float y, String[] cells)
            throws java.io.IOException {
        float colX = x + 4;
        for (int i = 0; i < cells.length; i++) {
            String txt = cells[i] == null ? "" : cells[i];
            int maxChars = (int) ((OP_COL_WIDTHS[i] - 8) / (fontSize * 0.55));
            if (txt.length() > maxChars && maxChars > 0) txt = txt.substring(0, maxChars);
            cs.beginText();
            cs.setFont(font, fontSize);
            cs.newLineAtOffset(colX, y);
            cs.showText(txt);
            cs.endText();
            colX += OP_COL_WIDTHS[i];
        }
    }

    private String translateOperation(String type) {
        if (type == null) return "—";
        return switch (type) {
            case "RECEIPT" -> "Приёмка";
            case "SHIPMENT" -> "Отгрузка";
            case "STAGING" -> "Размещение";
            case "TRANSFER" -> "Перемещение";
            case "WRITE_OFF" -> "Списание";
            case "REVALUATION" -> "Переоценка";
            case "INVENTORY" -> "Инвентаризация";
            default -> type;
        };
    }

    private static final java.time.format.DateTimeFormatter PDF_DATE_FMT =
            java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private String formatDate(Object dt) {
        if (dt == null) return "";
        try {
            if (dt instanceof java.time.LocalDateTime ldt) {
                return ldt.format(PDF_DATE_FMT);
            }
            if (dt instanceof java.time.OffsetDateTime odt) {
                return odt.format(PDF_DATE_FMT);
            }
            String s = dt.toString();
            int dot = s.indexOf('.');
            if (dot > 0) s = s.substring(0, dot);
            java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(s.replace(' ', 'T'));
            return ldt.format(PDF_DATE_FMT);
        } catch (Exception e) {
            String s = dt.toString();
            int dot = s.indexOf('.');
            if (dot > 0) s = s.substring(0, dot);
            return s.replace('T', ' ');
        }
    }

    private String warehouseLabel(Object idObj, Map<String, String> cache) {
        if (idObj == null) return "—";
        String key = idObj.toString();
        return cache.computeIfAbsent(key, k -> {
            try {
                Map<String, Object> wh = warehouseClient.getWarehouse(UUID.fromString(k), "WORKER");
                if (wh != null) {
                    Object name = wh.get("name");
                    if (name != null) return name.toString();
                }
            } catch (Exception ignored) {
            }
            return k.length() > 8 ? k.substring(0, 8) : k;
        });
    }

    private String formatQty(Object q) {
        if (q == null) return "0";
        try {
            java.math.BigDecimal bd = new java.math.BigDecimal(q.toString()).stripTrailingZeros();
            return bd.scale() < 0 ? bd.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString() : bd.toPlainString();
        } catch (NumberFormatException e) {
            return q.toString();
        }
    }

    private Map<String, Object> batchInfo(ProductBatch b) {
        Map<String, Object> m = new HashMap<>();
        m.put("batchId", b.getBatchId());
        m.put("batchNumber", b.getBatchNumber());
        m.put("manufactureDate", b.getManufactureDate());
        m.put("expiryDate", b.getExpiryDate());
        m.put("supplier", b.getSupplier());
        m.put("supplyId", b.getSupplyId());
        m.put("storageConditions", b.getStorageConditions());
        m.put("packagingType", b.getPackagingType());
        m.put("unitsPerPackage", b.getUnitsPerPackage());
        m.put("purchasePrice", b.getPurchasePrice());
        m.put("createdAt", b.getCreatedAt());
        return m;
    }

    private Map<String, Object> stockInfo(Inventory inv) {
        Map<String, Object> m = new HashMap<>();
        m.put("inventoryId", inv.getInventoryId());
        m.put("warehouseId", inv.getWarehouseId());
        m.put("cellId", inv.getCellId());
        m.put("batchId", inv.getBatchId());
        m.put("unitSku", inv.getUnitSku());
        m.put("quantity", inv.getQuantity());
        m.put("reservedQuantity", inv.getReservedQuantity());
        m.put("status", inv.getStatus());
        m.put("lastUpdated", inv.getLastUpdated());
        if (inv.getBatchId() != null) {
            batchRepository.findById(inv.getBatchId()).ifPresent(b -> {
                m.put("batchNumber", b.getBatchNumber());
                m.put("expiryDate", b.getExpiryDate());
                m.put("packagingType", b.getPackagingType());
                m.put("batchPackagingType", b.getPackagingType());
                m.put("unitsPerPackage", b.getUnitsPerPackage());
                m.put("storageConditions", b.getStorageConditions());
            });
        }
        return m;
    }

    private Map<String, Object> operationInfo(ProductOperation op) {
        Map<String, Object> m = new HashMap<>();
        m.put("operationId", op.getOperationId());
        m.put("operationType", op.getOperationType());
        m.put("operationDate", op.getOperationDate());
        m.put("warehouseId", op.getWarehouseId());
        m.put("fromCellId", op.getFromCellId());
        m.put("toCellId", op.getToCellId());
        m.put("quantity", op.getQuantity());
        m.put("userId", op.getUserId());
        m.put("notes", op.getNotes());
        return m;
    }
}
