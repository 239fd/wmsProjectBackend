package by.bsuir.productservice.service;

import by.bsuir.productservice.client.WarehouseClient;
import by.bsuir.productservice.client.dto.RackInfoDto;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.repository.InventoryRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class BarcodeService {

    private final InventoryRepository inventoryRepository;
    private final WarehouseClient warehouseClient;

    @Transactional
    public String assignSkuToInventory(UUID inventoryId, String userRole) {
        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> AppException.notFound("Запас не найден"));

        if (inventory.getCellId() == null) {
            throw AppException.badRequest("У запаса не указана ячейка — невозможно сгенерировать SKU");
        }
        if (inventory.getOrganizationId() == null || inventory.getWarehouseId() == null) {
            throw AppException.badRequest("Не указаны organizationId или warehouseId");
        }

        UUID rackId = resolveRackId(inventory.getCellId(), inventory.getWarehouseId(), userRole);
        if (rackId == null) {
            throw AppException.notFound("Стеллаж для ячейки не найден");
        }
        RackInfoDto rack = warehouseClient.getRack(rackId, userRole);
        if (rack == null) {
            throw AppException.notFound("Стеллаж не найден");
        }

        AtomicInteger counter = new AtomicInteger(1);
        String sku;
        int attempts = 0;
        do {
            sku = formatSku(
                    inventory.getOrganizationId(),
                    inventory.getWarehouseId(),
                    rackId,
                    rack.kind(),
                    counter.getAndIncrement());
            attempts++;
            if (attempts > 1000) {
                throw AppException.internalError("Невозможно подобрать уникальный SKU");
            }
        } while (inventoryRepository.findByOrganizationIdAndUnitSku(inventory.getOrganizationId(), sku).isPresent());

        inventory.setUnitSku(sku);
        inventoryRepository.save(inventory);
        log.info("Assigned SKU {} to inventory {}", sku, inventoryId);
        return sku;
    }

    public byte[] generateBarcodeSheetPdf(List<UUID> inventoryIds, UUID organizationId) {
        if (inventoryIds == null || inventoryIds.isEmpty()) {
            throw AppException.badRequest("Список inventoryIds пуст");
        }

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);

            float pageWidth = page.getMediaBox().getWidth();
            float startX = 40f;
            float startY = page.getMediaBox().getHeight() - 60f;
            float labelHeight = 80f;
            float yPos = startY;

            for (UUID inventoryId : inventoryIds) {
                Inventory inv = inventoryRepository.findById(inventoryId).orElse(null);
                if (inv == null) continue;
                if (organizationId != null && inv.getOrganizationId() != null
                        && !organizationId.equals(inv.getOrganizationId())) continue;
                if (inv.getUnitSku() == null) continue;

                String ean13 = toEan13(inv.getUnitSku());
                BufferedImage barcodeImg = renderEan13(ean13);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(barcodeImg, "PNG", baos);
                PDImageXObject pdImage = LosslessFactory.createFromImage(doc, ImageIO.read(new ByteArrayInputStream(baos.toByteArray())));

                cs.drawImage(pdImage, startX, yPos - 60, 200, 60);

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.newLineAtOffset(startX + 220, yPos - 20);
                cs.showText("SKU: " + inv.getUnitSku());
                cs.newLineAtOffset(0, -14);
                cs.showText("EAN-13: " + ean13);
                cs.newLineAtOffset(0, -14);
                cs.showText("Inventory: " + inv.getInventoryId());
                cs.endText();

                yPos -= labelHeight + 20;

                if (yPos < 80) {
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    yPos = page.getMediaBox().getHeight() - 60f;
                }
            }

            cs.close();
            doc.save(out);
            return out.toByteArray();
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate barcode sheet: {}", e.getMessage(), e);
            throw AppException.internalError("Ошибка генерации штрихкодов: " + e.getMessage());
        }
    }

    public String formatSku(UUID organizationId, UUID warehouseId, UUID rackId, String rackKind, int cellNumber) {
        int rackCount = warehouseClient.getRacksByWarehouse(warehouseId, "WORKER").size();
        String aaaa = hashMod4(organizationId);
        String bbbb = hashMod4(warehouseId);
        String cccc = String.format("%04d", Math.abs(stableHash(rackId)) % Math.max(rackCount, 1));
        char kindCode = rackKindCode(rackKind);
        String dddd = String.format("%c%03d", kindCode, cellNumber % 1000);
        return (aaaa + bbbb + cccc + dddd).toUpperCase();
    }

    private String hashMod4(UUID id) {
        if (id == null) return "0000";
        return String.format("%04d", Math.abs(stableHash(id)) % 10000);
    }

    private int stableHash(UUID id) {
        if (id == null) return 0;
        long lsb = id.getLeastSignificantBits();
        long msb = id.getMostSignificantBits();
        return (int) (lsb ^ msb);
    }

    private char rackKindCode(String rackKind) {
        if (rackKind == null) return 'X';
        return switch (rackKind) {
            case "SHELF" -> 'S';
            case "CELL" -> 'C';
            case "FRIDGE" -> 'F';
            case "PALLET" -> 'P';
            default -> 'X';
        };
    }

    private UUID resolveRackId(UUID cellId, UUID warehouseId, String userRole) {
        for (RackInfoDto rack : warehouseClient.getRacksByWarehouse(warehouseId, userRole)) {
            if (warehouseClient.getCellsByRack(rack.rackId(), userRole)
                    .stream().anyMatch(c -> cellId.equals(c.cellId()))) {
                return rack.rackId();
            }
        }
        return null;
    }

    private String toEan13(String sku) {
        StringBuilder digits = new StringBuilder();
        for (char c : sku.toCharArray()) {
            digits.append(Character.getNumericValue(c) & 0x0F);
        }
        String body = digits.length() >= 12 ? digits.substring(0, 12) : String.format("%-12s", digits).replace(' ', '0');
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int d = body.charAt(i) - '0';
            sum += (i % 2 == 0) ? d : d * 3;
        }
        int check = (10 - (sum % 10)) % 10;
        return body + check;
    }

    private BufferedImage renderEan13(String ean13) throws Exception {
        BitMatrix matrix = new MultiFormatWriter().encode(ean13, BarcodeFormat.EAN_13, 300, 100);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }
}
