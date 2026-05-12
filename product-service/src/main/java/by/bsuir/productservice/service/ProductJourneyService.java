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
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
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

    public byte[] generateJourneyPdf(UUID productId, UUID batchId, UUID inventoryId, UUID organizationId) {
        Map<String, Object> journey = getJourney(productId, batchId, inventoryId, organizationId);

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);

            float y = page.getMediaBox().getHeight() - 60f;
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
            cs.newLineAtOffset(40, y);
            cs.showText("Product Journey: " + journey.get("productName"));
            cs.endText();
            y -= 25;

            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 10);
            cs.newLineAtOffset(40, y);
            cs.showText("ProductId: " + productId);
            cs.newLineAtOffset(0, -14);
            cs.showText("SKU: " + journey.get("sku"));
            cs.newLineAtOffset(0, -14);
            cs.showText("ABC: " + journey.get("abcClass"));
            cs.endText();
            y -= 60;

            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
            cs.newLineAtOffset(40, y);
            cs.showText("Operations:");
            cs.endText();
            y -= 18;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> operations = (List<Map<String, Object>>) journey.get("operations");
            for (Map<String, Object> op : operations) {
                if (y < 80) {
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = page.getMediaBox().getHeight() - 60f;
                }
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 9);
                cs.newLineAtOffset(40, y);
                String line = String.format("%s | %s | wh=%s | qty=%s",
                        op.get("operationDate"), op.get("operationType"),
                        op.get("warehouseId"), op.get("quantity"));
                cs.showText(line.length() > 110 ? line.substring(0, 110) : line);
                cs.endText();
                y -= 12;
            }

            cs.close();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate journey PDF: {}", e.getMessage(), e);
            throw AppException.internalError("Ошибка генерации PDF карточки товара: " + e.getMessage());
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
