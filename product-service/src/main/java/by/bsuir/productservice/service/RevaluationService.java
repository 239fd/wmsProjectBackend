package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.RevaluationRequest;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.enums.InventoryEventType;
import by.bsuir.productservice.model.enums.OperationType;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevaluationService {

    private final ProductReadModelRepository productRepository;
    private final ProductOperationRepository operationRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryEventService inventoryEventService;

    @Transactional
    public Map<String, Object> revaluate(RevaluationRequest request, UUID organizationId) {
        log.info("Revaluating product {} at warehouse {} to new price {} (org: {})",
                request.productId(), request.warehouseId(), request.newPrice(), organizationId);

        ProductReadModel product = productRepository.findById(request.productId())
                .orElseThrow(() -> AppException.notFound("Товар не найден"));

        if (organizationId != null && product.getOrganizationId() != null
                && !organizationId.equals(product.getOrganizationId())) {
            throw AppException.forbidden("Товар принадлежит другой организации");
        }

        BigDecimal oldPrice = product.getPrice();
        if (oldPrice != null && oldPrice.compareTo(request.newPrice()) == 0) {
            throw AppException.badRequest("Новая цена совпадает с текущей");
        }

        UUID effectiveOrgId = organizationId != null ? organizationId : product.getOrganizationId();

        product.setPrice(request.newPrice());
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);

        StringBuilder notes = new StringBuilder();
        if (request.reason() != null) notes.append("reason=").append(request.reason()).append("; ");
        if (request.basis() != null) notes.append("basis=").append(request.basis()).append("; ");
        if (request.responsibleUserId() != null) notes.append("responsible=").append(request.responsibleUserId()).append("; ");
        if (request.commissionMembers() != null && !request.commissionMembers().isEmpty()) {
            notes.append("commission=").append(request.commissionMembers().stream()
                    .map(UUID::toString).collect(Collectors.joining(","))).append("; ");
        }
        notes.append("oldPrice=").append(oldPrice).append("; newPrice=").append(request.newPrice());
        if (request.notes() != null) notes.append("; ").append(request.notes());

        ProductOperation operation = ProductOperation.builder()
                .operationId(UUID.randomUUID())
                .operationType(OperationType.REVALUATION)
                .productId(request.productId())
                .organizationId(effectiveOrgId)
                .warehouseId(request.warehouseId())
                .quantity(BigDecimal.ZERO)
                .userId(request.userId())
                .operationDate(LocalDateTime.now())
                .notes(notes.toString())
                .build();
        operationRepository.save(operation);

        List<Inventory> affected = inventoryRepository
                .findByProductIdAndWarehouseId(request.productId(), request.warehouseId())
                .map(List::of)
                .orElseGet(() -> inventoryRepository.findByProductId(request.productId()));
        for (Inventory inv : affected) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("productId", request.productId());
            payload.put("warehouseId", request.warehouseId());
            payload.put("oldPrice", oldPrice);
            payload.put("newPrice", request.newPrice());
            payload.put("operationId", operation.getOperationId());
            payload.put("userId", request.userId());
            payload.put("reason", request.reason());
            inventoryEventService.record(inv.getInventoryId(), InventoryEventType.REVALUED, payload);
        }

        log.info("Revaluation completed. Operation ID: {}", operation.getOperationId());

        Map<String, Object> result = new HashMap<>();
        result.put("operationId", operation.getOperationId());
        result.put("productId", product.getProductId());
        result.put("oldPrice", oldPrice);
        result.put("newPrice", request.newPrice());
        result.put("responsibleUserId", request.responsibleUserId());
        result.put("commissionMembers", request.commissionMembers());
        return result;
    }
}
