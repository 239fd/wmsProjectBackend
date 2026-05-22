package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.import_.SupplyDto;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.entity.Supplier;
import by.bsuir.productservice.model.entity.Supply;
import by.bsuir.productservice.model.entity.SupplyItem;
import by.bsuir.productservice.model.enums.PackagingType;
import by.bsuir.productservice.model.enums.StorageConditions;
import by.bsuir.productservice.model.enums.SupplyStatus;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import by.bsuir.productservice.repository.SupplierRepository;
import by.bsuir.productservice.repository.SupplyItemRepository;
import by.bsuir.productservice.repository.SupplyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplyImportService {

    private final SupplyRepository supplyRepository;
    private final SupplyItemRepository supplyItemRepository;
    private final SupplierRepository supplierRepository;
    private final ProductReadModelRepository productRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ImportResult importSupplies(
            UUID organizationId,
            UUID warehouseFallback,
            UUID userId,
            String source,
            List<SupplyDto> supplies) {

        if (organizationId == null) {
            throw AppException.badRequest("organizationId обязателен для импорта поставок");
        }
        if (supplies == null || supplies.isEmpty()) {
            return new ImportResult(0, 0, 0, List.of());
        }

        int imported = 0;
        int skipped = 0;
        int errored = 0;
        List<String> errors = new ArrayList<>();

        for (SupplyDto dto : supplies) {
            try {
                if (dto.externalId() != null && !dto.externalId().isBlank()) {
                    Optional<Supply> existing = supplyRepository
                            .findByOrganizationIdAndExternalId(organizationId, dto.externalId());
                    if (existing.isPresent()) {
                        skipped++;
                        continue;
                    }
                }
                UUID warehouseId = dto.warehouseId() != null ? dto.warehouseId() : warehouseFallback;
                if (warehouseId == null) {
                    errored++;
                    errors.add("Поставка " + dto.externalId() + ": не указан warehouseId");
                    continue;
                }

                Supplier supplier = resolveSupplier(organizationId, dto.supplier());
                Supply supply = Supply.builder()
                        .supplyId(UUID.randomUUID())
                        .organizationId(organizationId)
                        .supplierId(supplier != null ? supplier.getSupplierId() : null)
                        .supplierName(supplier != null ? supplier.getName() :
                                (dto.supplier() != null ? dto.supplier().name() : null))
                        .warehouseId(warehouseId)
                        .status(SupplyStatus.PLANNED)
                        .externalId(dto.externalId())
                        .source(source != null ? source : "MANUAL")
                        .quantityOnly(Boolean.TRUE.equals(dto.quantityOnly()))
                        .expectedDate(dto.expectedDate())
                        .currency(dto.currency())
                        .totalAmount(dto.totalAmount())
                        .totalItems(itemsCount(dto))
                        .notes(dto.notes())
                        .snapshot(serializeSnapshot(dto.snapshot()))
                        .createdBy(userId)
                        .build();

                if (!Boolean.TRUE.equals(dto.quantityOnly()) && dto.items() != null) {
                    int rowNumber = 1;
                    for (SupplyDto.SupplyItemDto itemDto : dto.items()) {
                        SupplyItem item = buildItem(organizationId, supply.getSupplyId(), rowNumber, itemDto);
                        supply.getItems().add(item);
                        rowNumber++;
                    }
                }
                supplyRepository.save(supply);

                imported++;
            } catch (Exception ex) {
                errored++;
                errors.add("Поставка " + dto.externalId() + ": " + ex.getMessage());
                log.error("Не удалось импортировать поставку {}: {}", dto.externalId(), ex.getMessage(), ex);
            }
        }

        log.info("Импорт поставок ({}): импортировано={}, пропущено={}, с ошибкой={}",
                source, imported, skipped, errored);
        return new ImportResult(imported, skipped, errored, errors);
    }

    private int itemsCount(SupplyDto dto) {
        if (Boolean.TRUE.equals(dto.quantityOnly()) && dto.totalItems() != null) {
            return dto.totalItems();
        }
        return dto.items() != null ? dto.items().size() : 0;
    }

    private Supplier resolveSupplier(UUID organizationId, SupplyDto.SupplierDto dto) {
        if (dto == null) return null;
        String unp = dto.unp() != null ? dto.unp() : dto.inn();
        Optional<Supplier> match;
        if (unp != null && !unp.isBlank()) {
            match = supplierRepository.findFirstByOrganizationIdAndUnp(organizationId, unp);
        } else if (dto.name() != null && !dto.name().isBlank()) {
            match = supplierRepository.findFirstByOrganizationIdAndNameIgnoreCase(organizationId, dto.name());
        } else {
            return null;
        }
        if (match.isPresent()) return match.get();

        Supplier created = Supplier.builder()
                .organizationId(organizationId)
                .name(dto.name() != null ? dto.name() : "Поставщик")
                .unp(unp)
                .contactPerson(dto.contactPerson())
                .phone(dto.phone())
                .email(dto.email())
                .address(dto.address())
                .isActive(Boolean.TRUE)
                .build();
        return supplierRepository.save(created);
    }

    private SupplyItem buildItem(
            UUID organizationId, UUID supplyId, int rowNumber, SupplyDto.SupplyItemDto dto) {

        ProductReadModel product = resolveProduct(organizationId, dto.product());
        SupplyDto.ProductDto productDto = dto.product();
        SupplyDto.BatchDto batchDto = dto.batch();

        BigDecimal expectedQty = dto.expectedQty() != null ? dto.expectedQty() : BigDecimal.ZERO;

        return SupplyItem.builder()
                .supplyId(supplyId)
                .productId(product != null ? product.getProductId() : null)
                .rowNumber(dto.rowNumber() != null ? dto.rowNumber() : rowNumber)
                .productName(productDto != null ? productDto.name() : null)
                .sku(productDto != null ? productDto.sku() : null)
                .barcode(productDto != null ? productDto.barcode() : null)
                .category(productDto != null ? productDto.category() : null)
                .unitOfMeasure(productDto != null ? productDto.unitOfMeasure() : null)
                .manufacturer(productDto != null ? productDto.manufacturer() : null)
                .storageConditions(parseStorageConditions(
                        productDto != null ? productDto.storageConditions() : null))
                .expectedQty(expectedQty)
                .actualQty(dto.actualQty())
                .unitPrice(dto.unitPrice())
                .vatRate(dto.vatRate())
                .vatAmount(dto.vatAmount())
                .totalAmount(dto.totalAmount())
                .packagingType(parsePackagingType(dto.packagingType()))
                .unitsPerPackage(dto.unitsPerPackage())
                .batchNumber(batchDto != null ? batchDto.batchNumber() : null)
                .manufactureDate(batchDto != null ? batchDto.manufactureDate() : null)
                .expiryDate(batchDto != null ? batchDto.expiryDate() : null)
                .purchasePrice(batchDto != null ? batchDto.purchasePrice() : null)
                .markedForWriteoff(Boolean.TRUE.equals(dto.markedForWriteoff()))
                .notes(dto.notes())
                .build();
    }

    private ProductReadModel resolveProduct(UUID organizationId, SupplyDto.ProductDto dto) {
        if (dto == null || dto.sku() == null || dto.sku().isBlank()) return null;
        Optional<ProductReadModel> match = productRepository.findBySku(dto.sku());
        if (match.isPresent() && match.get().getOrganizationId() != null
                && match.get().getOrganizationId().equals(organizationId)) {
            return match.get();
        }
        ProductReadModel created = ProductReadModel.builder()
                .productId(UUID.randomUUID())
                .organizationId(organizationId)
                .name(dto.name() != null ? dto.name() : dto.sku())
                .sku(dto.sku())
                .barcode(dto.barcode())
                .category(dto.category())
                .description(dto.description())
                .unitOfMeasure(dto.unitOfMeasure())
                .weightKg(dto.weightKg())
                .volumeM3(dto.volumeM3())
                .price(dto.price())
                .abcClass("C")
                .requiredStorageCondition(parseStorageConditions(dto.storageConditions()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return productRepository.save(created);
    }

    private StorageConditions parseStorageConditions(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.toUpperCase(Locale.ROOT)
                .replace("ROOM_TEMP", "ROOM")
                .replace("AMBIENT", "ROOM")
                .replace("DRY", "ROOM");
        try {
            return StorageConditions.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            log.warn("Неизвестные условия хранения: {}", raw);
            return null;
        }
    }

    private PackagingType parsePackagingType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return PackagingType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("Неизвестный тип упаковки: {}", raw);
            return null;
        }
    }

    private String serializeSnapshot(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            log.warn("Не удалось сериализовать snapshot: {}", ex.getMessage());
            return null;
        }
    }

    public record ImportResult(int imported, int skipped, int errored, List<String> errors) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("imported", imported);
            m.put("skipped", skipped);
            m.put("errored", errored);
            m.put("errors", errors);
            return m;
        }
    }
}
