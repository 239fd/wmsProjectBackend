package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.CreateSupplyRequest;
import by.bsuir.productservice.dto.response.SupplyResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Supply;
import by.bsuir.productservice.model.entity.SupplyItem;
import by.bsuir.productservice.model.enums.SupplyStatus;
import by.bsuir.productservice.repository.SupplyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplyService {

    private final SupplyRepository supplyRepository;

    @Transactional(readOnly = true)
    public List<SupplyResponse> getAll() {
        return supplyRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<SupplyResponse> getAll(Pageable pageable) {
        return supplyRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SupplyResponse getById(UUID supplyId) {
        return toResponse(find(supplyId));
    }

    @Transactional(readOnly = true)
    public List<SupplyResponse> getByWarehouse(UUID warehouseId) {
        return supplyRepository.findByWarehouseId(warehouseId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<SupplyResponse> getByWarehouse(UUID warehouseId, Pageable pageable) {
        return supplyRepository.findByWarehouseId(warehouseId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<SupplyResponse> getByStatus(SupplyStatus status) {
        return supplyRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<SupplyResponse> getByStatus(SupplyStatus status, Pageable pageable) {
        return supplyRepository.findByStatus(status, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<SupplyResponse> getByOrganization(UUID organizationId) {
        return supplyRepository.findByOrganizationId(organizationId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<SupplyResponse> getByOrganization(UUID organizationId, Pageable pageable) {
        return supplyRepository.findByOrganizationId(organizationId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<SupplyResponse> getByOrganizationAndStatus(UUID organizationId, SupplyStatus status) {
        return supplyRepository.findByOrganizationIdAndStatus(organizationId, status).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<SupplyResponse> getByOrganizationAndStatus(UUID organizationId, SupplyStatus status, Pageable pageable) {
        return supplyRepository.findByOrganizationIdAndStatus(organizationId, status, pageable).map(this::toResponse);
    }

    @Transactional
    public SupplyResponse create(CreateSupplyRequest request) {
        return create(request, null, null, null);
    }

    @Transactional
    public SupplyResponse create(CreateSupplyRequest request, UUID organizationId) {
        return create(request, organizationId, null, null);
    }

    @Transactional
    public SupplyResponse create(CreateSupplyRequest request, UUID organizationId, UUID warehouseFallback) {
        return create(request, organizationId, warehouseFallback, null);
    }

    @Transactional
    public SupplyResponse create(CreateSupplyRequest request, UUID organizationId,
                                 UUID warehouseFallback, UUID userFallback) {
        UUID warehouseId = request.warehouseId() != null ? request.warehouseId() : warehouseFallback;
        if (warehouseId == null) {
            throw AppException.badRequest("Склад обязателен");
        }
        UUID createdBy = request.createdBy() != null ? request.createdBy() : userFallback;
        if (createdBy == null) {
            throw AppException.badRequest("User ID обязателен");
        }
        log.info("Creating supply for warehouse: {} by user: {}", warehouseId, createdBy);

        boolean quantityOnly = Boolean.TRUE.equals(request.quantityOnly());
        UUID supplyId = UUID.randomUUID();

        List<SupplyItem> items = new ArrayList<>();
        if (!quantityOnly && request.items() != null) {
            int rowNumber = 1;
            for (CreateSupplyRequest.SupplyItemRequest itemReq : request.items()) {
                items.add(SupplyItem.builder()
                        .supplyId(supplyId)
                        .productId(itemReq.productId())
                        .rowNumber(rowNumber++)
                        .productName(itemReq.productName())
                        .sku(itemReq.sku())
                        .barcode(itemReq.barcode())
                        .category(itemReq.category())
                        .unitOfMeasure(itemReq.unitOfMeasure())
                        .manufacturer(itemReq.manufacturer())
                        .storageConditions(parseConditions(itemReq.storageConditions()))
                        .expectedQty(itemReq.expectedQty() != null ? itemReq.expectedQty() : BigDecimal.ZERO)
                        .unitPrice(itemReq.unitPrice())
                        .vatRate(itemReq.vatRate())
                        .vatAmount(itemReq.vatAmount())
                        .totalAmount(itemReq.totalAmount())
                        .packagingType(itemReq.packagingType())
                        .unitsPerPackage(itemReq.unitsPerPackage())
                        .packageLengthCm(itemReq.packageLengthCm())
                        .packageWidthCm(itemReq.packageWidthCm())
                        .packageHeightCm(itemReq.packageHeightCm())
                        .packageWeightKg(itemReq.packageWeightKg())
                        .batchNumber(itemReq.batchNumber())
                        .manufactureDate(itemReq.manufactureDate())
                        .expiryDate(itemReq.expiryDate())
                        .purchasePrice(itemReq.purchasePrice())
                        .markedForWriteoff(Boolean.TRUE.equals(itemReq.markedForWriteoff()))
                        .notes(itemReq.notes())
                        .build());
            }
        }

        int totalItems = quantityOnly
                ? (request.totalItems() != null ? request.totalItems() : 0)
                : items.size();

        Supply supply = Supply.builder()
                .supplyId(supplyId)
                .organizationId(organizationId)
                .supplierId(request.supplierId())
                .supplierName(request.supplierName())
                .warehouseId(warehouseId)
                .status(SupplyStatus.PLANNED)
                .source("MANUAL")
                .quantityOnly(quantityOnly)
                .expectedDate(request.expectedDate())
                .currency(request.currency())
                .totalAmount(request.totalAmount())
                .totalItems(totalItems)
                .notes(request.notes())
                .createdBy(createdBy)
                .items(items)
                .build();

        supplyRepository.save(supply);
        log.info("Supply created: {}", supply.getSupplyId());
        return toResponse(supply);
    }

    private by.bsuir.productservice.model.enums.StorageConditions parseConditions(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return by.bsuir.productservice.model.enums.StorageConditions.valueOf(
                    raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Transactional
    public SupplyResponse updateStatus(UUID supplyId, SupplyStatus newStatus, UUID userId) {
        log.info("Updating supply {} status to {}", supplyId, newStatus);
        Supply supply = find(supplyId);

        validateTransition(supply.getStatus(), newStatus);

        supply.setStatus(newStatus);
        if (newStatus == SupplyStatus.ACCEPTED || newStatus == SupplyStatus.REJECTED) {
            supply.setActualDate(LocalDate.now());
        }

        supplyRepository.save(supply);
        log.info("Supply {} status updated to {}", supplyId, newStatus);
        return toResponse(supply);
    }

    @Transactional
    public void delete(UUID supplyId) {
        Supply supply = find(supplyId);
        if (supply.getStatus() != SupplyStatus.PLANNED) {
            throw AppException.badRequest("Можно удалить только поставку в статусе PLANNED");
        }
        supplyRepository.delete(supply);
    }

    @Transactional
    public SupplyResponse update(UUID supplyId, CreateSupplyRequest request, UUID organizationId) {
        Supply supply = find(supplyId);
        if (supply.getStatus() != SupplyStatus.PLANNED) {
            throw AppException.badRequest("Изменять можно только поставку в статусе PLANNED");
        }
        if (organizationId != null && supply.getOrganizationId() != null
                && !organizationId.equals(supply.getOrganizationId())) {
            throw AppException.forbidden("Нет доступа к поставке");
        }

        boolean quantityOnly = request.quantityOnly() != null
                ? request.quantityOnly()
                : Boolean.TRUE.equals(supply.getQuantityOnly());
        if (request.supplierId() != null) supply.setSupplierId(request.supplierId());
        if (request.supplierName() != null && !request.supplierName().isBlank()) {
            supply.setSupplierName(request.supplierName());
        }
        if (request.warehouseId() != null) supply.setWarehouseId(request.warehouseId());
        if (request.expectedDate() != null) supply.setExpectedDate(request.expectedDate());
        if (request.currency() != null) supply.setCurrency(request.currency());
        if (request.totalAmount() != null) supply.setTotalAmount(request.totalAmount());
        supply.setNotes(request.notes());
        supply.setQuantityOnly(quantityOnly);

        if (supply.getItems() != null) supply.getItems().clear();

        List<SupplyItem> newItems = new ArrayList<>();
        if (!quantityOnly && request.items() != null) {
            int rowNumber = 1;
            for (CreateSupplyRequest.SupplyItemRequest itemReq : request.items()) {
                newItems.add(SupplyItem.builder()
                        .supplyId(supply.getSupplyId())
                        .productId(itemReq.productId())
                        .rowNumber(rowNumber++)
                        .productName(itemReq.productName())
                        .sku(itemReq.sku())
                        .barcode(itemReq.barcode())
                        .category(itemReq.category())
                        .unitOfMeasure(itemReq.unitOfMeasure())
                        .manufacturer(itemReq.manufacturer())
                        .storageConditions(parseConditions(itemReq.storageConditions()))
                        .expectedQty(itemReq.expectedQty() != null ? itemReq.expectedQty() : BigDecimal.ZERO)
                        .unitPrice(itemReq.unitPrice())
                        .vatRate(itemReq.vatRate())
                        .vatAmount(itemReq.vatAmount())
                        .totalAmount(itemReq.totalAmount())
                        .packagingType(itemReq.packagingType())
                        .unitsPerPackage(itemReq.unitsPerPackage())
                        .packageLengthCm(itemReq.packageLengthCm())
                        .packageWidthCm(itemReq.packageWidthCm())
                        .packageHeightCm(itemReq.packageHeightCm())
                        .packageWeightKg(itemReq.packageWeightKg())
                        .batchNumber(itemReq.batchNumber())
                        .manufactureDate(itemReq.manufactureDate())
                        .expiryDate(itemReq.expiryDate())
                        .purchasePrice(itemReq.purchasePrice())
                        .markedForWriteoff(Boolean.TRUE.equals(itemReq.markedForWriteoff()))
                        .notes(itemReq.notes())
                        .build());
            }
        }
        if (supply.getItems() != null) {
            supply.getItems().addAll(newItems);
        } else {
            supply.setItems(newItems);
        }
        if (quantityOnly) {
            if (request.totalItems() != null && request.totalItems() > 0) {
                supply.setTotalItems(request.totalItems());
            }
        } else {
            supply.setTotalItems(newItems.size());
        }
        log.info("Updating supply {}: req.quantityOnly={}, req.totalItems={}, req.supplierId={}, req.supplierName={}, req.expectedDate={}, req.items.size={}, persisted.totalItems={}",
                supplyId, request.quantityOnly(), request.totalItems(),
                request.supplierId(), request.supplierName(), request.expectedDate(),
                request.items() == null ? null : request.items().size(),
                supply.getTotalItems());
        Supply persisted = supplyRepository.saveAndFlush(supply);
        return toResponse(persisted);
    }

    private void validateTransition(SupplyStatus current, SupplyStatus next) {
        boolean valid = switch (current) {
            case PLANNED -> next == SupplyStatus.IN_PROGRESS || next == SupplyStatus.CANCELLED;
            case IN_PROGRESS -> next == SupplyStatus.ACCEPTED || next == SupplyStatus.REJECTED;
            default -> false;
        };
        if (!valid) {
            throw AppException.badRequest(
                    String.format("Недопустимый переход статуса: %s → %s", current, next));
        }
    }

    private Supply find(UUID supplyId) {
        return supplyRepository.findById(supplyId)
                .orElseThrow(() -> AppException.notFound("Поставка не найдена"));
    }

    private SupplyResponse toResponse(Supply s) {
        List<SupplyResponse.SupplyItemResponse> itemResponses = s.getItems() == null
                ? List.of()
                : s.getItems().stream()
                        .map(i -> new SupplyResponse.SupplyItemResponse(
                                i.getItemId(),
                                i.getProductId(),
                                i.getRowNumber(),
                                i.getProductName(),
                                i.getSku(),
                                i.getBarcode(),
                                i.getCategory(),
                                i.getUnitOfMeasure(),
                                i.getManufacturer(),
                                i.getStorageConditions(),
                                i.getExpectedQty(),
                                i.getActualQty(),
                                i.getUnitPrice(),
                                i.getVatRate(),
                                i.getVatAmount(),
                                i.getTotalAmount(),
                                i.getPackagingType(),
                                i.getUnitsPerPackage(),
                                i.getPackageLengthCm(),
                                i.getPackageWidthCm(),
                                i.getPackageHeightCm(),
                                i.getPackageWeightKg(),
                                i.getBatchNumber(),
                                i.getManufactureDate(),
                                i.getExpiryDate(),
                                i.getPurchasePrice(),
                                i.getMarkedForWriteoff(),
                                i.getNotes()))
                        .collect(Collectors.toList());

        return new SupplyResponse(
                s.getSupplyId(),
                s.getOrganizationId(),
                s.getSupplierId(),
                s.getSupplierName(),
                s.getWarehouseId(),
                s.getStatus(),
                s.getExternalId(),
                s.getSource(),
                s.getQuantityOnly(),
                s.getExpectedDate(),
                s.getActualDate(),
                s.getTotalItems(),
                s.getCurrency(),
                s.getTotalAmount(),
                s.getNotes(),
                s.getCreatedBy(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                itemResponses
        );
    }
}