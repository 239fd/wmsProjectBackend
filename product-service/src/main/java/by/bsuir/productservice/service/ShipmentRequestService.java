package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.CompleteShipmentRequest;
import by.bsuir.productservice.dto.request.CreateShipmentRequestRequest;
import by.bsuir.productservice.dto.request.PickRequest;
import by.bsuir.productservice.dto.response.ShipmentRequestResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.GeneratedDocument;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductBatch;
import by.bsuir.productservice.model.entity.ProductReadModel;
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
import by.bsuir.productservice.repository.ProductBatchRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
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
    private final ProductBatchRepository batchRepository;
    private final ProductReadModelRepository productRepository;
    private final FEFOService fefoService;
    private final InventoryEventService inventoryEventService;
    private final DocumentRegistryService documentRegistryService;
    private final by.bsuir.productservice.client.WarehouseClient warehouseClient;

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
        AllocationStrategy strategy = request.strategy() != null ? request.strategy() : AllocationStrategy.AUTO;

        if (shipmentType == ShipmentType.EXPORT && "BYN".equals(currency)) {
            throw AppException.badRequest("Для экспортной отгрузки укажите валюту контракта (USD/EUR/RUB)");
        }
        if (shipmentType == ShipmentType.EXPORT
                && (request.recipientCountry() == null || request.recipientCountry().isBlank())) {
            throw AppException.badRequest("Для экспортной отгрузки укажите страну получателя");
        }
        verifyWarehouseBelongsToOrg(request.warehouseId(), organizationId);

        Map<UUID, List<FEFOService.InventoryAllocation>> allocationsByProduct =
                allocate(request.items(), request.warehouseId(), strategy, "создать заявку");

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
                .strategy(strategy)
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

        reserveAndPersistItems(entity.getRequestId(), allocationsByProduct);

        try {
            List<ShipmentRequestItem> all = itemRepository.findByRequestId(entity.getRequestId());
            Map<String, Object> pickingPayload = buildPickingListPayload(entity, all,
                    documentLayout, currency);
            GeneratedDocument picking = documentRegistryService.register(null, "picking-list", pickingPayload,
                    organizationId, userId != null ? userId : entity.getCreatedBy());
            entity.setPickingListDocId(picking.getId());
            requestRepository.save(entity);
        } catch (Exception ex) {
            log.warn("Не удалось сгенерировать picking-list для заявки {}: {}",
                    entity.getRequestId(), ex.getMessage());
            entity.setDocumentError("Не удалось сгенерировать лист подбора — перевыпустите позже");
            requestRepository.save(entity);
        }

        log.info("Shipment request {} created with {} items (strategy={})",
                entity.getRequestId(),
                allocationsByProduct.values().stream().mapToInt(List::size).sum(),
                strategy);
        return mapToResponse(entity, List.of());
    }

    @Transactional
    public ShipmentRequestResponse addItems(UUID requestId,
                                            by.bsuir.productservice.dto.request.AddShipmentItemsRequest request,
                                            UUID userId, UUID organizationId) {
        ShipmentRequest entity = findOwned(requestId, organizationId);
        if (entity.getStatus() == ShipmentRequestStatus.COMPLETED
                || entity.getStatus() == ShipmentRequestStatus.CANCELLED) {
            throw AppException.badRequest(
                    "Нельзя добавлять позиции в завершённую или отменённую заявку");
        }

        AllocationStrategy strategy = entity.getStrategy() != null
                ? entity.getStrategy() : AllocationStrategy.AUTO;

        Map<UUID, List<FEFOService.InventoryAllocation>> allocationsByProduct =
                allocate(request.items(), entity.getWarehouseId(), strategy, "добавить позиции");

        reserveAndPersistItems(entity.getRequestId(), allocationsByProduct);

        entity.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(entity);

        try {
            List<ShipmentRequestItem> all = itemRepository.findByRequestId(entity.getRequestId());
            DocumentLayout layout = entity.getDocumentLayout() != null
                    ? entity.getDocumentLayout() : DocumentLayout.HORIZONTAL;
            String currency = entity.getCurrency() != null ? entity.getCurrency() : "BYN";
            Map<String, Object> pickingPayload = buildPickingListPayload(entity, all, layout, currency);
            UUID prevPicking = entity.getPickingListDocId();
            GeneratedDocument picking = documentRegistryService.register(null, "picking-list", pickingPayload,
                    organizationId, userId != null ? userId : entity.getCreatedBy());
            entity.setPickingListDocId(picking.getId());
            requestRepository.save(entity);
            documentRegistryService.markSuperseded(prevPicking, picking.getId(), organizationId);
        } catch (Exception ex) {
            log.warn("Не удалось пересоздать picking-list для заявки {}: {}",
                    entity.getRequestId(), ex.getMessage());
        }

        log.info("Added {} new line(s) to shipment request {} (strategy={})",
                allocationsByProduct.values().stream().mapToInt(List::size).sum(),
                entity.getRequestId(), strategy);
        return mapToResponse(entity, List.of());
    }

    private Map<UUID, List<FEFOService.InventoryAllocation>> allocate(
            List<CreateShipmentRequestRequest.Item> items,
            UUID warehouseId,
            AllocationStrategy strategy,
            String actionLabel) {
        Map<UUID, BigDecimal> aggregatedByProduct = new HashMap<>();
        for (CreateShipmentRequestRequest.Item itemReq : items) {
            aggregatedByProduct.merge(itemReq.productId(), itemReq.expectedQty(), BigDecimal::add);
        }

        Map<UUID, List<FEFOService.InventoryAllocation>> allocationsByProduct = new HashMap<>();
        for (Map.Entry<UUID, BigDecimal> entry : aggregatedByProduct.entrySet()) {
            UUID productId = entry.getKey();
            BigDecimal needed = entry.getValue();
            List<FEFOService.InventoryAllocation> allocations;
            try {
                allocations = fefoService.selectInventory(productId, warehouseId, needed, strategy);
            } catch (AppException e) {
                ProductReadModel p = productRepository.findById(productId).orElse(null);
                String prodName = p != null ? p.getName() : productId.toString();
                throw AppException.badRequest(
                        "Невозможно " + actionLabel + ": товар «" + prodName + "» — " + e.getMessage());
            }
            allocationsByProduct.put(productId, allocations);
        }
        return allocationsByProduct;
    }

    private void reserveAndPersistItems(UUID requestId,
                                        Map<UUID, List<FEFOService.InventoryAllocation>> allocationsByProduct) {
        for (Map.Entry<UUID, List<FEFOService.InventoryAllocation>> e : allocationsByProduct.entrySet()) {
            UUID productId = e.getKey();
            for (FEFOService.InventoryAllocation alloc : e.getValue()) {
                Inventory inventory = inventoryRepository.findByIdForUpdate(alloc.getInventoryId())
                        .orElseThrow(() -> AppException.notFound("Inventory не найден при резерве"));
                BigDecimal available = inventory.getQuantity().subtract(inventory.getReservedQuantity());
                if (available.compareTo(alloc.getQuantity()) < 0) {
                    throw AppException.conflict(
                            "Конфликт резервирования: партия "
                                    + alloc.getBatchId() + " уже зарезервирована другой заявкой");
                }
                inventory.setReservedQuantity(inventory.getReservedQuantity().add(alloc.getQuantity()));
                inventory.setLastUpdated(LocalDateTime.now());
                inventoryRepository.save(inventory);

                ShipmentRequestItem item = ShipmentRequestItem.builder()
                        .itemId(UUID.randomUUID())
                        .requestId(requestId)
                        .productId(productId)
                        .batchId(alloc.getBatchId())
                        .inventoryId(alloc.getInventoryId())
                        .cellId(alloc.getCellId())
                        .expectedQty(alloc.getQuantity())
                        .pickedQty(BigDecimal.ZERO)
                        .unitSku(inventory.getUnitSku())
                        .status("PENDING")
                        .build();
                itemRepository.save(item);
            }
        }
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
            if (item == null) {
                item = findItemByProductBarcode(requestId, pick.unitSku());
            }
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
        return complete(requestId, userId, organizationId, null);
    }

    @Transactional
    public ShipmentRequestResponse complete(UUID requestId, UUID userId, UUID organizationId,
                                            CompleteShipmentRequest manual) {
        ShipmentRequest req = findOwned(requestId, organizationId);
        if (req.getStatus() == ShipmentRequestStatus.COMPLETED) {
            throw AppException.badRequest("Заявка уже завершена");
        }
        if (req.getStatus() == ShipmentRequestStatus.CANCELLED) {
            throw AppException.badRequest("Отменённую заявку нельзя завершить");
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
            if (item.getInventoryId() == null) {
                throw AppException.conflict(
                        "Позиция " + item.getItemId() + " не привязана к inventory — пересоздайте заявку");
            }
            Inventory inventory = inventoryRepository.findByIdForUpdate(item.getInventoryId())
                    .orElseThrow(() -> AppException.notFound("Inventory не найден при завершении заявки"));

            BigDecimal qty = item.getExpectedQty();
            BigDecimal qtyBefore = inventory.getQuantity();
            inventory.setQuantity(qtyBefore.subtract(qty));
            inventory.setReservedQuantity(inventory.getReservedQuantity().subtract(qty).max(BigDecimal.ZERO));
            inventory.setLastUpdated(LocalDateTime.now());
            inventoryRepository.save(inventory);

            if (inventory.getQuantity().compareTo(BigDecimal.ZERO) <= 0
                    && (inventory.getReservedQuantity() == null
                        || inventory.getReservedQuantity().compareTo(BigDecimal.ZERO) <= 0)) {
                inventoryRepository.delete(inventory);
                log.info("Inventory {} отгружен полностью → удалён (ячейка освобождена)",
                        inventory.getInventoryId());
            }

            ProductOperation operation = ProductOperation.builder()
                    .operationId(UUID.randomUUID())
                    .operationType(OperationType.SHIPMENT)
                    .productId(item.getProductId())
                    .batchId(item.getBatchId())
                    .organizationId(req.getOrganizationId())
                    .warehouseId(req.getWarehouseId())
                    .fromCellId(item.getCellId())
                    .quantity(qty)
                    .userId(req.getCreatedBy())
                    .operationDate(LocalDateTime.now())
                    .notes(String.format("Отгрузка по заявке %s (стратегия %s)", requestId, strategy))
                    .build();
            operationRepository.save(operation);
            if (primaryOperationId == null) primaryOperationId = operation.getOperationId();

            inventoryEventService.recordQuantityChange(inventory, InventoryEventType.ITEM_REMOVED,
                    qtyBefore, qty.negate(),
                    operation.getOperationId(), req.getCreatedBy(),
                    Map.of("requestId", requestId, "strategy", strategy.name()));

            if (item.getCellId() != null && item.getBatchId() != null) {
                BigDecimal heightDelta = computeHeightDelta(item.getBatchId(), qty);
                if (heightDelta.signum() > 0) {
                    warehouseClient.adjustSlotHeight(item.getCellId(), heightDelta);
                }
            }
        }

        List<UUID> generatedIds = generateShipmentDocuments(req, items, primaryOperationId,
                userId != null ? userId : req.getCreatedBy(), organizationId, manual);

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
        if (req.getStatus() == ShipmentRequestStatus.CANCELLED) {
            return;
        }
        List<ShipmentRequestItem> items = itemRepository.findByRequestId(requestId);
        for (ShipmentRequestItem item : items) {
            if (item.getInventoryId() == null) continue;
            Inventory inventory = inventoryRepository.findByIdForUpdate(item.getInventoryId())
                    .orElse(null);
            if (inventory == null) continue;
            BigDecimal released = inventory.getReservedQuantity().subtract(item.getExpectedQty());
            inventory.setReservedQuantity(released.max(BigDecimal.ZERO));
            inventory.setLastUpdated(LocalDateTime.now());
            inventoryRepository.save(inventory);
        }
        req.setStatus(ShipmentRequestStatus.CANCELLED);
        req.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(req);
        log.info("Shipment request {} cancelled, released reservation on {} items", requestId, items.size());
    }

    private List<UUID> generateShipmentDocuments(
            ShipmentRequest req,
            List<ShipmentRequestItem> items,
            UUID operationId,
            UUID userId,
            UUID organizationId,
            CompleteShipmentRequest manual) {

        ShipmentType shipmentType = req.getShipmentType() != null ? req.getShipmentType() : ShipmentType.DOMESTIC;
        DocumentLayout layout = req.getDocumentLayout() != null ? req.getDocumentLayout() : DocumentLayout.HORIZONTAL;
        DomesticDocumentKind kind = req.getDomesticDocumentKind() != null
                ? req.getDomesticDocumentKind() : DomesticDocumentKind.TN;
        String currency = req.getCurrency() != null ? req.getCurrency() : "BYN";

        Map<String, Object> basePayload = buildBasePayload(req, items, layout, currency);
        mergeManualFields(basePayload, manual);
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
        return buildPayload(req, items, layout, currency, false);
    }

    private Map<String, Object> buildPickingListPayload(
            ShipmentRequest req,
            List<ShipmentRequestItem> items,
            DocumentLayout layout,
            String currency) {
        return buildPayload(req, items, layout, currency, true);
    }

    private Map<String, Object> buildPayload(
            ShipmentRequest req,
            List<ShipmentRequestItem> items,
            DocumentLayout layout,
            String currency,
            boolean forPickingList) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("requestId", req.getRequestId().toString());
        payload.put("shipmentNumber", "ЗО-" + String.valueOf(req.getRequestId()).substring(0, 8).toUpperCase());
        payload.put("documentDate", java.time.LocalDate.now().toString());
        payload.put("senderOrganizationId",
                req.getOrganizationId() != null ? req.getOrganizationId().toString() : null);
        payload.put("warehouseId", req.getWarehouseId() != null ? req.getWarehouseId().toString() : null);
        payload.put("recipientName", req.getRecipientName());
        payload.put("recipientAddress", req.getRecipientAddress());
        payload.put("recipientInn", req.getRecipientInn());
        payload.put("consigneeName", req.getRecipientName());
        payload.put("consigneeAddress", req.getRecipientAddress());
        payload.put("consigneeInn", req.getRecipientInn());
        payload.put("payerName", req.getRecipientName());
        payload.put("payerInn", req.getRecipientInn());
        payload.put("recipientCountry", req.getRecipientCountry());
        payload.put("recipientGln", req.getRecipientGln());
        payload.put("plannedDate", req.getPlannedDate() != null ? req.getPlannedDate().toString() : null);
        payload.put("shipmentDate", LocalDateTime.now().toLocalDate().toString());
        payload.put("layout", layout.name().toLowerCase());
        payload.put("currency", currency);
        payload.put("shipmentType", req.getShipmentType() != null ? req.getShipmentType().name() : "DOMESTIC");
        payload.put("strategy", req.getStrategy() != null ? req.getStrategy().name() : "AUTO");
        payload.put("comment", req.getComment());

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;
        BigDecimal totalWeightKg = BigDecimal.ZERO;
        BigDecimal totalVolumeM3 = BigDecimal.ZERO;
        int totalLines = 0;

        Map<UUID, Map<String, String>> locationCache = new HashMap<>();

        List<Map<String, Object>> itemPayloads = new ArrayList<>();
        for (ShipmentRequestItem i : items) {
            ProductReadModel product = i.getProductId() != null
                    ? productRepository.findById(i.getProductId()).orElse(null) : null;
            ProductBatch batch = i.getBatchId() != null
                    ? batchRepository.findById(i.getBatchId()).orElse(null) : null;

            BigDecimal qty = forPickingList
                    ? (i.getExpectedQty() != null ? i.getExpectedQty() : BigDecimal.ZERO)
                    : (i.getPickedQty() != null && i.getPickedQty().compareTo(BigDecimal.ZERO) > 0
                            ? i.getPickedQty() : i.getExpectedQty());
            BigDecimal unitPrice = batch != null && batch.getPurchasePrice() != null
                    ? batch.getPurchasePrice() : BigDecimal.ZERO;
            BigDecimal vatRate = new BigDecimal("20");
            BigDecimal lineNet = qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineVat = lineNet.multiply(vatRate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal lineGross = lineNet.add(lineVat);
            BigDecimal lineWeight = (batch != null && batch.getPackageWeightKg() != null
                    && batch.getUnitsPerPackage() != null && batch.getUnitsPerPackage() > 0)
                    ? batch.getPackageWeightKg()
                        .multiply(qty)
                        .divide(BigDecimal.valueOf(batch.getUnitsPerPackage()), 3, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal lineVolume = BigDecimal.ZERO;
            if (batch != null && batch.getPackageLengthCm() != null && batch.getPackageWidthCm() != null
                    && batch.getPackageHeightCm() != null
                    && batch.getUnitsPerPackage() != null && batch.getUnitsPerPackage() > 0) {
                BigDecimal pkgVolM3 = batch.getPackageLengthCm()
                        .multiply(batch.getPackageWidthCm())
                        .multiply(batch.getPackageHeightCm())
                        .divide(new BigDecimal("1000000"), 6, RoundingMode.HALF_UP);
                BigDecimal numPkgs = qty.divide(
                        BigDecimal.valueOf(batch.getUnitsPerPackage()), 6, RoundingMode.HALF_UP);
                lineVolume = pkgVolM3.multiply(numPkgs).setScale(4, RoundingMode.HALF_UP);
            }

            Map<String, String> loc = resolveLocation(i.getCellId(), locationCache);
            String unitLabel = product != null && product.getUnitOfMeasure() != null
                    ? product.getUnitOfMeasure() : "шт";

            Map<String, Object> m = new HashMap<>();
            m.put("lineNo", ++totalLines);
            m.put("productId", i.getProductId() != null ? i.getProductId().toString() : null);
            m.put("productName", product != null ? product.getName() : null);
            m.put("name", product != null ? product.getName() : null);
            m.put("productSku", product != null ? product.getSku() : null);
            m.put("sku", product != null ? product.getSku() : null);
            m.put("barcode", product != null ? product.getBarcode() : null);
            m.put("unitOfMeasure", unitLabel);
            m.put("unit", unitLabel);
            m.put("batchId", i.getBatchId() != null ? i.getBatchId().toString() : null);
            m.put("batchNumber", batch != null ? batch.getBatchNumber() : null);
            m.put("expiryDate", batch != null && batch.getExpiryDate() != null
                    ? batch.getExpiryDate().toString() : null);
            m.put("quantity", qty != null ? qty.stripTrailingZeros().toPlainString() : "0");
            m.put("unitPrice", unitPrice);
            m.put("price", unitPrice);
            m.put("vatRate", vatRate);
            m.put("lineNet", lineNet);
            m.put("lineVat", lineVat);
            m.put("lineGross", lineGross);
            m.put("totalPrice", lineNet);
            m.put("vatAmount", lineVat);
            m.put("totalWithVat", lineGross);
            m.put("amount", lineGross);
            m.put("weightKg", lineWeight);
            m.put("grossWeight", lineWeight);
            m.put("grossWeightKg", lineWeight);
            m.put("volumeM3", lineVolume);
            m.put("declaredValue", lineGross);
            m.put("packagingType", batch != null && batch.getPackagingType() != null
                    ? batch.getPackagingType().name() : null);
            m.put("marks", batch != null ? batch.getBatchNumber() : null);
            m.put("cellId", i.getCellId() != null ? i.getCellId().toString() : null);
            m.put("rackName", loc.get("rackName"));
            m.put("cellCode", loc.get("cellCode"));
            m.put("location", loc.get("location"));
            m.put("unitSku", i.getUnitSku());
            itemPayloads.add(m);

            subtotal = subtotal.add(lineNet);
            totalVat = totalVat.add(lineVat);
            grandTotal = grandTotal.add(lineGross);
            totalWeightKg = totalWeightKg.add(lineWeight);
            totalVolumeM3 = totalVolumeM3.add(lineVolume);
        }
        payload.put("items", itemPayloads);

        BigDecimal totalQty = items.stream()
                .map(it -> forPickingList
                        ? (it.getExpectedQty() != null ? it.getExpectedQty() : BigDecimal.ZERO)
                        : (it.getPickedQty() != null && it.getPickedQty().compareTo(BigDecimal.ZERO) > 0
                                ? it.getPickedQty() : it.getExpectedQty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        payload.put("totalQuantity", totalQty.stripTrailingZeros().toPlainString());
        payload.put("totalAmount", subtotal);
        payload.put("totalVat", totalVat);
        payload.put("totalAmountWithVat", grandTotal);
        payload.put("totalAmountInWords",
                by.bsuir.productservice.util.MoneyToWordsRu.rubles(grandTotal));
        payload.put("totalGrossWeight", totalWeightKg);
        payload.put("totalNetWeight", totalWeightKg);
        payload.put("totalWeight", totalWeightKg);
        payload.put("totalVolume", totalVolumeM3);
        payload.put("cargoDeclaredValue", grandTotal);
        payload.put("totalLines", totalLines);
        payload.put("totalSeats", totalLines);

        Map<String, Object> totals = new HashMap<>();
        totals.put("lines", totalLines);
        totals.put("subtotal", subtotal);
        totals.put("vatTotal", totalVat);
        totals.put("grandTotal", grandTotal);
        totals.put("totalWeightKg", totalWeightKg);
        totals.put("currency", currency);
        payload.put("totals", totals);

        return payload;
    }

    private Map<String, String> resolveLocation(UUID cellId, Map<UUID, Map<String, String>> cache) {
        Map<String, String> empty = Map.of("rackName", "—", "cellCode", "—", "location", "— / —");
        if (cellId == null) return empty;
        if (cache.containsKey(cellId)) return cache.get(cellId);

        String rackName = "—";
        String cellCode = String.valueOf(cellId).substring(0, 8).toUpperCase();
        try {
            Map<String, Object> info = warehouseClient.getCellInfo(cellId, "WORKER");
            if (info != null) {
                if (info.get("slotCode") != null) {
                    cellCode = info.get("slotCode").toString();
                }
                if (info.get("rackId") != null) {
                    UUID rackId = UUID.fromString(info.get("rackId").toString());
                    var rack = warehouseClient.getRack(rackId, "WORKER");
                    if (rack != null && rack.name() != null) rackName = rack.name();
                }
            }
        } catch (Exception ex) {
            log.debug("resolveLocation: failed for cell {}: {}", cellId, ex.getMessage());
        }
        Map<String, String> result = Map.of(
                "rackName", rackName,
                "cellCode", cellCode,
                "location", rackName + " / " + cellCode);
        cache.put(cellId, result);
        return result;
    }

    private ShipmentRequestItem findItemByProductBarcode(UUID requestId, String scanned) {
        ProductReadModel product = productRepository.findByBarcode(scanned)
                .or(() -> productRepository.findBySku(scanned))
                .orElse(null);
        if (product == null) {
            return null;
        }
        return itemRepository.findByRequestId(requestId).stream()
                .filter(it -> product.getProductId().equals(it.getProductId()))
                .filter(it -> !"PICKED".equals(it.getStatus()))
                .min(java.util.Comparator.comparing(
                        it -> it.getExpectedQty() != null ? it.getExpectedQty() : BigDecimal.ZERO))
                .orElse(null);
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

    private void verifyWarehouseBelongsToOrg(UUID warehouseId, UUID organizationId) {
        if (warehouseId == null || organizationId == null) {
            return;
        }
        try {
            Map<String, Object> wh = warehouseClient.getWarehouse(warehouseId, "WORKER");
            if (wh == null) {
                throw AppException.notFound("Склад не найден");
            }
            Object whOrg = wh.get("orgId");
            if (whOrg != null && !organizationId.toString().equals(whOrg.toString())) {
                throw AppException.forbidden("Склад принадлежит другой организации");
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn("verifyWarehouseBelongsToOrg: не удалось проверить склад {}: {}",
                    warehouseId, e.getMessage());
        }
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
                .map(i -> {
                    ProductBatch b = i.getBatchId() != null
                            ? batchRepository.findById(i.getBatchId()).orElse(null)
                            : null;
                    ProductReadModel p = i.getProductId() != null
                            ? productRepository.findById(i.getProductId()).orElse(null)
                            : null;
                    return new ShipmentRequestResponse.Item(
                            i.getItemId(), i.getProductId(), i.getBatchId(),
                            i.getInventoryId(), i.getCellId(),
                            i.getExpectedQty(), i.getPickedQty(), i.getUnitSku(), i.getStatus(),
                            b != null ? b.getBatchNumber() : null,
                            b != null ? b.getExpiryDate() : null,
                            p != null ? p.getName() : null,
                            p != null ? p.getSku() : null);
                })
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

    private BigDecimal computeHeightDelta(UUID batchId, BigDecimal quantityUnits) {
        if (batchId == null || quantityUnits == null || quantityUnits.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        ProductBatch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null || batch.getPackageHeightCm() == null) return BigDecimal.ZERO;
        int upp = (batch.getUnitsPerPackage() != null && batch.getUnitsPerPackage() > 0)
                ? batch.getUnitsPerPackage() : 1;
        BigDecimal numPackages = quantityUnits.divide(
                BigDecimal.valueOf(upp), 0, java.math.RoundingMode.CEILING);
        return batch.getPackageHeightCm().multiply(numPackages);
    }

    private void mergeManualFields(Map<String, Object> payload, CompleteShipmentRequest manual) {
        if (manual == null) return;

        putIfNonBlank(payload, "vehicleMake", manual.vehicleMake());
        putIfNonBlank(payload, "vehicleNumber", manual.vehicleNumber());
        putIfNonBlank(payload, "vehiclePlate", manual.vehicleNumber());
        putIfNonBlank(payload, "trailerNumber", manual.trailerNumber());
        putIfNonBlank(payload, "trailerPlate", manual.trailerNumber());
        putIfNonBlank(payload, "driverName", manual.driverName());
        putIfNonBlank(payload, "driverFullName", manual.driverName());
        putIfNonBlank(payload, "proxyNumber", manual.proxyNumber());
        if (manual.proxyDate() != null) {
            payload.put("proxyDate", manual.proxyDate().toString());
        }
        putIfNonBlank(payload, "proxyIssuedBy", manual.proxyIssuedBy());
        putIfNonBlank(payload, "sealNumber", manual.sealNumber());
        putIfNonBlank(payload, "contractNumber", manual.contractNumber());
        if (manual.contractDate() != null) {
            payload.put("contractDate", manual.contractDate().toString());
        }
        putIfNonBlank(payload, "accompanyingDocs", manual.accompanyingDocs());

        putIfNonBlank(payload, "carrierName", manual.carrierName());
        putIfNonBlank(payload, "carrierInn", manual.carrierInn());
        putIfNonBlank(payload, "carrierUnp", manual.carrierInn());
        putIfNonBlank(payload, "carrierAddress", manual.carrierAddress());
        putIfNonBlank(payload, "carrierPhone", manual.carrierPhone());
        putIfNonBlank(payload, "countryOfManufacture", manual.countryOfManufacture());

        putIntPair(payload, "loadingArrivalHour", "loading_arrival_hour", manual.loadingArrivalHour());
        putIntPair(payload, "loadingArrivalMin", "loading_arrival_min", manual.loadingArrivalMin());
        putIntPair(payload, "loadingDepartureHour", "loading_departure_hour", manual.loadingDepartureHour());
        putIntPair(payload, "loadingDepartureMin", "loading_departure_min", manual.loadingDepartureMin());
        putIntPair(payload, "unloadingArrivalHour", "unloading_arrival_hour", manual.unloadingArrivalHour());
        putIntPair(payload, "unloadingArrivalMin", "unloading_arrival_min", manual.unloadingArrivalMin());
        putIntPair(payload, "unloadingDepartureHour", "unloading_departure_hour", manual.unloadingDepartureHour());
        putIntPair(payload, "unloadingDepartureMin", "unloading_departure_min", manual.unloadingDepartureMin());

        putIfNonBlank(payload, "paymentTerms", manual.paymentTerms());
        putIfNonBlank(payload, "paymentInstructions", manual.paymentTerms());
        putIfNonBlank(payload, "specialTerms", manual.specialTerms());
        putIfNonBlank(payload, "specialInstructions", manual.specialTerms());
        putIfNonBlank(payload, "shipperInstructions", manual.shipperInstructions());
        putIfNonBlank(payload, "carrierRemarks", manual.carrierRemarks());
    }

    private static void putIfNonBlank(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value);
        }
    }

    private static void putIntPair(Map<String, Object> payload, String camelKey, String snakeKey, Integer value) {
        if (value != null) {
            payload.put(camelKey, value);
            payload.put(snakeKey, value);
        }
    }
}
