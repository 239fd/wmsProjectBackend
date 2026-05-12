package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.CreateShipmentRequestRequest;
import by.bsuir.productservice.dto.request.PickRequest;
import by.bsuir.productservice.dto.response.ShipmentRequestResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ShipmentRequest;
import by.bsuir.productservice.model.entity.ShipmentRequestItem;
import by.bsuir.productservice.model.enums.AllocationStrategy;
import by.bsuir.productservice.model.enums.InventoryEventType;
import by.bsuir.productservice.model.enums.OperationType;
import by.bsuir.productservice.model.enums.ShipmentRequestStatus;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ShipmentRequestItemRepository;
import by.bsuir.productservice.repository.ShipmentRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentRequestService {

    private final ShipmentRequestRepository requestRepository;
    private final ShipmentRequestItemRepository itemRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductOperationRepository operationRepository;
    private final FEFOService fefoService;
    private final InventoryEventService inventoryEventService;

    @Transactional
    public ShipmentRequestResponse create(CreateShipmentRequestRequest request, UUID userId, UUID organizationId) {
        log.info("Creating shipment request for warehouse {} (org={}, user={})",
                request.warehouseId(), organizationId, userId);

        ShipmentRequest entity = ShipmentRequest.builder()
                .requestId(UUID.randomUUID())
                .organizationId(organizationId)
                .warehouseId(request.warehouseId())
                .recipientName(request.recipientName())
                .recipientAddress(request.recipientAddress())
                .recipientInn(request.recipientInn())
                .plannedDate(request.plannedDate())
                .comment(request.comment())
                .status(ShipmentRequestStatus.PLANNED)
                .strategy(request.strategy() != null ? request.strategy() : AllocationStrategy.AUTO)
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        requestRepository.save(entity);

        for (CreateShipmentRequestRequest.Item itemReq : request.items()) {
            ShipmentRequestItem item = ShipmentRequestItem.builder()
                    .itemId(UUID.randomUUID())
                    .requestId(entity.getRequestId())
                    .productId(itemReq.productId())
                    .batchId(itemReq.batchId())
                    .expectedQty(itemReq.expectedQty())
                    .pickedQty(BigDecimal.ZERO)
                    .status("PENDING")
                    .build();
            itemRepository.save(item);
        }

        return mapToResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<ShipmentRequestResponse> getAll(UUID organizationId) {
        List<ShipmentRequest> reqs = (organizationId != null)
                ? requestRepository.findByOrganizationId(organizationId)
                : requestRepository.findAll();
        return reqs.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ShipmentRequestResponse get(UUID requestId, UUID organizationId) {
        ShipmentRequest entity = findOwned(requestId, organizationId);
        return mapToResponse(entity);
    }

    @Transactional
    public ShipmentRequestResponse pick(UUID requestId, PickRequest pick, UUID organizationId) {
        ShipmentRequest req = findOwned(requestId, organizationId);

        if (req.getStatus() == ShipmentRequestStatus.COMPLETED || req.getStatus() == ShipmentRequestStatus.CANCELLED) {
            throw AppException.badRequest("Заявка уже завершена или отменена");
        }
        if (req.getStatus() == ShipmentRequestStatus.PLANNED) {
            req.setStatus(ShipmentRequestStatus.PICKING);
            requestRepository.save(req);
        }

        ShipmentRequestItem item;
        if (pick.unitSku() != null) {
            item = itemRepository.findByRequestIdAndUnitSku(requestId, pick.unitSku())
                    .orElseGet(() -> findItemByInventorySku(requestId, pick.unitSku()));
        } else {
            throw AppException.badRequest("Требуется unitSku");
        }
        if (item == null) {
            throw AppException.notFound("Позиция для штрихкода не найдена в заявке");
        }

        if ("PICKED".equals(item.getStatus()) && pick.unitSku().equals(item.getUnitSku())) {
            log.info("Idempotent pick of {} — already picked, no-op", pick.unitSku());
            return mapToResponse(req);
        }

        BigDecimal newPicked = item.getPickedQty().add(pick.qty());
        if (newPicked.compareTo(item.getExpectedQty()) > 0) {
            throw AppException.badRequest("Превышено ожидаемое количество для позиции");
        }
        item.setPickedQty(newPicked);
        if (pick.unitSku() != null && item.getUnitSku() == null) {
            item.setUnitSku(pick.unitSku());
        }
        if (newPicked.compareTo(item.getExpectedQty()) == 0) {
            item.setStatus("PICKED");
        } else {
            item.setStatus("PARTIAL");
        }
        itemRepository.save(item);

        return mapToResponse(req);
    }

    @Transactional
    public ShipmentRequestResponse unpick(UUID requestId, PickRequest pick, UUID organizationId) {
        ShipmentRequest req = findOwned(requestId, organizationId);

        ShipmentRequestItem item;
        if (pick.unitSku() != null) {
            item = itemRepository.findByRequestIdAndUnitSku(requestId, pick.unitSku())
                    .orElseThrow(() -> AppException.notFound("Позиция не найдена"));
        } else {
            throw AppException.badRequest("Требуется unitSku");
        }

        BigDecimal newPicked = item.getPickedQty().subtract(pick.qty());
        if (newPicked.compareTo(BigDecimal.ZERO) < 0) {
            newPicked = BigDecimal.ZERO;
        }
        item.setPickedQty(newPicked);
        item.setStatus(newPicked.compareTo(BigDecimal.ZERO) == 0 ? "PENDING" : "PARTIAL");
        itemRepository.save(item);

        return mapToResponse(req);
    }

    @Transactional
    public ShipmentRequestResponse complete(UUID requestId, List<String> documentTypes, UUID organizationId) {
        ShipmentRequest req = findOwned(requestId, organizationId);
        if (req.getStatus() == ShipmentRequestStatus.COMPLETED) {
            throw AppException.badRequest("Заявка уже завершена");
        }
        List<ShipmentRequestItem> items = itemRepository.findByRequestId(requestId);
        boolean allPicked = items.stream()
                .allMatch(i -> i.getPickedQty().compareTo(i.getExpectedQty()) >= 0);
        if (!allPicked) {
            throw AppException.badRequest("Не все позиции собраны");
        }

        AllocationStrategy strategy = req.getStrategy() != null ? req.getStrategy() : AllocationStrategy.AUTO;

        for (ShipmentRequestItem item : items) {
            List<FEFOService.InventoryAllocation> allocations = fefoService.selectInventory(
                    item.getProductId(),
                    req.getWarehouseId(),
                    item.getPickedQty(),
                    strategy);

            for (FEFOService.InventoryAllocation allocation : allocations) {
                Inventory inventory = inventoryRepository.findById(allocation.getInventoryId())
                        .orElseThrow(() -> AppException.notFound("Inventory не найден"));
                BigDecimal qtyBefore = inventory.getQuantity();
                inventory.setQuantity(qtyBefore.subtract(allocation.getQuantity()));
                inventory.setLastUpdated(LocalDateTime.now());
                inventoryRepository.save(inventory);

                ProductOperation operation = ProductOperation.builder()
                        .operationId(UUID.randomUUID())
                        .operationType(OperationType.SHIPMENT)
                        .productId(allocation.getProductId())
                        .batchId(allocation.getBatchId())
                        .organizationId(req.getOrganizationId())
                        .warehouseId(allocation.getWarehouseId())
                        .fromCellId(allocation.getCellId())
                        .quantity(allocation.getQuantity())
                        .userId(req.getCreatedBy())
                        .operationDate(LocalDateTime.now())
                        .notes(String.format("Отгрузка по заявке %s (стратегия %s)", requestId, strategy))
                        .build();
                operationRepository.save(operation);

                inventoryEventService.recordQuantityChange(inventory, InventoryEventType.ITEM_REMOVED,
                        qtyBefore, allocation.getQuantity().negate(),
                        operation.getOperationId(), req.getCreatedBy(),
                        java.util.Map.of("requestId", requestId, "strategy", strategy.name()));
            }
        }

        req.setStatus(ShipmentRequestStatus.COMPLETED);
        req.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(req);
        log.info("Shipment request {} completed (strategy={}). Documents requested: {}",
                requestId, strategy, documentTypes);
        return mapToResponse(req);
    }

    @Transactional
    public void cancel(UUID requestId, UUID organizationId) {
        ShipmentRequest req = findOwned(requestId, organizationId);
        if (req.getStatus() == ShipmentRequestStatus.COMPLETED) {
            throw AppException.badRequest("Завершённую заявку нельзя отменить");
        }
        req.setStatus(ShipmentRequestStatus.CANCELLED);
        req.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(req);
    }

    private ShipmentRequestItem findItemByInventorySku(UUID requestId, String unitSku) {
        Inventory inv = inventoryRepository.findByUnitSku(unitSku).orElse(null);
        if (inv == null) return null;
        return itemRepository
                .findByRequestIdAndProductIdAndBatchId(requestId, inv.getProductId(), inv.getBatchId())
                .orElse(null);
    }

    private ShipmentRequest findOwned(UUID requestId, UUID organizationId) {
        ShipmentRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> AppException.notFound("Заявка на отгрузку не найдена"));
        if (organizationId != null && req.getOrganizationId() != null
                && !organizationId.equals(req.getOrganizationId())) {
            throw AppException.forbidden("Заявка принадлежит другой организации");
        }
        return req;
    }

    private ShipmentRequestResponse mapToResponse(ShipmentRequest entity) {
        List<ShipmentRequestItem> items = itemRepository.findByRequestId(entity.getRequestId());
        BigDecimal totalExpected = items.stream()
                .map(ShipmentRequestItem::getExpectedQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPicked = items.stream()
                .map(ShipmentRequestItem::getPickedQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal progress = (totalExpected.compareTo(BigDecimal.ZERO) > 0)
                ? totalPicked.multiply(BigDecimal.valueOf(100)).divide(totalExpected, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<ShipmentRequestResponse.Item> itemDtos = items.stream()
                .map(i -> new ShipmentRequestResponse.Item(
                        i.getItemId(), i.getProductId(), i.getBatchId(),
                        i.getExpectedQty(), i.getPickedQty(), i.getUnitSku(), i.getStatus()))
                .collect(Collectors.toList());

        return new ShipmentRequestResponse(
                entity.getRequestId(),
                entity.getOrganizationId(),
                entity.getWarehouseId(),
                entity.getRecipientName(),
                entity.getRecipientAddress(),
                entity.getRecipientInn(),
                entity.getPlannedDate(),
                entity.getComment(),
                entity.getStatus(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                progress,
                itemDtos
        );
    }
}
