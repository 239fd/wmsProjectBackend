package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.CreateShipmentRequestRequest;
import by.bsuir.productservice.dto.request.PickRequest;
import by.bsuir.productservice.dto.response.ShipmentRequestResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.GeneratedDocument;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ShipmentRequest;
import by.bsuir.productservice.model.entity.ShipmentRequestItem;
import by.bsuir.productservice.model.enums.AllocationStrategy;
import by.bsuir.productservice.model.enums.DocumentLayout;
import by.bsuir.productservice.model.enums.DomesticDocumentKind;
import by.bsuir.productservice.model.enums.InventoryEventType;
import by.bsuir.productservice.model.enums.OperationType;
import by.bsuir.productservice.model.enums.ShipmentRequestStatus;
import by.bsuir.productservice.model.enums.ShipmentType;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ShipmentRequestItemRepository;
import by.bsuir.productservice.repository.ShipmentRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final DocumentRegistryService documentRegistryService;

    @Transactional
    public ShipmentRequestResponse create(CreateShipmentRequestRequest request, UUID userId, UUID organizationId) {
        log.info("Creating shipment request for warehouse {} (org={}, user={}, type={})",
                request.warehouseId(), organizationId, userId, request.shipmentType());

        ShipmentType shipmentType = request.shipmentType() != null ? request.shipmentType() : ShipmentType.DOMESTIC;
        String currency = request.currency() != null ? request.currency().toUpperCase() : "BYN";
        DocumentLayout documentLayout = request.documentLayout() != null
                ? request.documentLayout()
                : DocumentLayout.HORIZONTAL;
        DomesticDocumentKind documentKind = request.domesticDocumentKind() != null
                ? request.domesticDocumentKind()
                : DomesticDocumentKind.TN;

        if (shipmentType == ShipmentType.EXPORT && "BYN".equals(currency)) {
            throw AppException.badRequest("Для экспортной отгрузки укажите валюту контракта (USD/EUR/RUB)");
        }

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
                .shipmentType(shipmentType)
                .currency(currency)
                .documentLayout(documentLayout)
                .domesticDocumentKind(documentKind)
                .recipientCountry(request.recipientCountry())
                .recipientGln(request.recipientGln())
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

        return mapToResponse(entity, List.of());
    }

    @Transactional(readOnly = true)
    public List<ShipmentRequestResponse> getAll(UUID organizationId) {
        List<ShipmentRequest> reqs = (organizationId != null)
                ? requestRepository.findByOrganizationId(organizationId)
                : requestRepository.findAll();
        return reqs.stream().map(r -> mapToResponse(r, List.of())).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ShipmentRequestResponse> getAll(UUID organizationId, Pageable pageable) {
        Page<ShipmentRequest> reqs = (organizationId != null)
                ? requestRepository.findByOrganizationId(organizationId, pageable)
                : requestRepository.findAll(pageable);
        return reqs.map(r -> mapToResponse(r, List.of()));
    }

    @Transactional(readOnly = true)
    public ShipmentRequestResponse get(UUID requestId, UUID organizationId) {
        ShipmentRequest entity = findOwned(requestId, organizationId);
        return mapToResponse(entity, List.of());
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
            return mapToResponse(req, List.of());
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

        return mapToResponse(req, List.of());
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

        return mapToResponse(req, List.of());
    }

    @Transactional
    public ShipmentRequestResponse complete(UUID requestId, UUID userId, UUID organizationId) {
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
        UUID primaryOperationId = null;

        for (ShipmentRequestItem item : items) {
            List<FEFOService.InventoryAllocation> allocations = fefoService.selectInventory(
                    item.getProductId(),
                    req.getWarehouseId(),
                    item.getPickedQty(),
                    strategy);

            for (FEFOService.InventoryAllocation allocation : allocations) {
                Inventory inventory = inventoryRepository.findByIdForUpdate(allocation.getInventoryId())
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
                if (primaryOperationId == null) primaryOperationId = operation.getOperationId();

                inventoryEventService.recordQuantityChange(inventory, InventoryEventType.ITEM_REMOVED,
                        qtyBefore, allocation.getQuantity().negate(),
                        operation.getOperationId(), req.getCreatedBy(),
                        Map.of("requestId", requestId, "strategy", strategy.name()));
            }
        }

        List<UUID> generatedIds = generateShipmentDocuments(req, items, primaryOperationId,
                userId != null ? userId : req.getCreatedBy(), organizationId);

        req.setStatus(ShipmentRequestStatus.COMPLETED);
        req.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(req);
        log.info("Shipment request {} completed (strategy={}, type={}). Documents generated: {}",
                requestId, strategy, req.getShipmentType(), generatedIds);
        return mapToResponse(req, generatedIds);
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

    private List<UUID> generateShipmentDocuments(
            ShipmentRequest req,
            List<ShipmentRequestItem> items,
            UUID operationId,
            UUID userId,
            UUID organizationId) {

        ShipmentType shipmentType = req.getShipmentType() != null ? req.getShipmentType() : ShipmentType.DOMESTIC;
        DocumentLayout layout = req.getDocumentLayout() != null ? req.getDocumentLayout() : DocumentLayout.HORIZONTAL;
        DomesticDocumentKind kind = req.getDomesticDocumentKind() != null
                ? req.getDomesticDocumentKind() : DomesticDocumentKind.TN;
        String currency = req.getCurrency() != null ? req.getCurrency() : "BYN";

        Map<String, Object> basePayload = buildBasePayload(req, items, layout, currency);
        List<UUID> result = new ArrayList<>();

        if (shipmentType == ShipmentType.EXPORT) {
            for (String type : List.of("transport-note", "cmr", "invoice")) {
                GeneratedDocument doc = documentRegistryService.register(
                        operationId, type, basePayload, organizationId, userId);
                result.add(doc.getId());
            }
        } else {
            String type = kind == DomesticDocumentKind.TTN ? "waybill" : "transport-note";
            GeneratedDocument doc = documentRegistryService.register(
                    operationId, type, basePayload, organizationId, userId);
            result.add(doc.getId());
        }
        return result;
    }

    private Map<String, Object> buildBasePayload(
            ShipmentRequest req,
            List<ShipmentRequestItem> items,
            DocumentLayout layout,
            String currency) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("requestId", req.getRequestId().toString());
        payload.put("warehouseId", req.getWarehouseId() != null ? req.getWarehouseId().toString() : null);
        payload.put("recipientName", req.getRecipientName());
        payload.put("recipientAddress", req.getRecipientAddress());
        payload.put("recipientInn", req.getRecipientInn());
        payload.put("recipientCountry", req.getRecipientCountry());
        payload.put("recipientGln", req.getRecipientGln());
        payload.put("plannedDate", req.getPlannedDate() != null ? req.getPlannedDate().toString() : null);
        payload.put("layout", layout.name().toLowerCase());
        payload.put("currency", currency);
        payload.put("shipmentType", req.getShipmentType() != null ? req.getShipmentType().name() : "DOMESTIC");
        payload.put("comment", req.getComment());

        List<Map<String, Object>> itemPayloads = items.stream().map(i -> {
            Map<String, Object> m = new HashMap<>();
            m.put("productId", i.getProductId().toString());
            m.put("batchId", i.getBatchId() != null ? i.getBatchId().toString() : null);
            m.put("quantity", i.getPickedQty());
            m.put("unitSku", i.getUnitSku());
            return m;
        }).collect(Collectors.toList());
        payload.put("items", itemPayloads);

        return payload;
    }

    private ShipmentRequestItem findItemByInventorySku(UUID requestId, String unitSku) {
        Inventory inv = inventoryRepository.findByUnitSku(unitSku).orElse(null);
        if (inv == null) return null;
        ShipmentRequestItem exact = inv.getBatchId() != null
                ? itemRepository
                        .findByRequestIdAndProductIdAndBatchId(requestId, inv.getProductId(), inv.getBatchId())
                        .orElse(null)
                : null;
        if (exact != null) return exact;
        return itemRepository
                .findFirstByRequestIdAndProductIdAndBatchIdIsNull(requestId, inv.getProductId())
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

    private ShipmentRequestResponse mapToResponse(ShipmentRequest entity, List<UUID> documentIds) {
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
                entity.getShipmentType(),
                entity.getCurrency(),
                entity.getDocumentLayout(),
                entity.getDomesticDocumentKind(),
                entity.getRecipientCountry(),
                entity.getRecipientGln(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                progress,
                documentIds != null ? documentIds : List.of(),
                itemDtos
        );
    }
}
