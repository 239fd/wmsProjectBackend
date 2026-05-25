package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.CreateReceiptSessionRequest;
import by.bsuir.productservice.dto.request.ReceiveProductRequest;
import by.bsuir.productservice.dto.request.SessionDiscrepancyRequest;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.GeneratedDocument;
import by.bsuir.productservice.model.entity.ProductBatch;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.entity.ReceiptSession;
import by.bsuir.productservice.model.entity.Supplier;
import by.bsuir.productservice.model.entity.Supply;
import by.bsuir.productservice.model.enums.OperationStatus;
import by.bsuir.productservice.model.enums.ReceiptSessionStatus;
import by.bsuir.productservice.model.enums.StorageConditions;
import by.bsuir.productservice.model.enums.SupplyStatus;
import by.bsuir.productservice.repository.ProductBatchRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import by.bsuir.productservice.repository.ReceiptSessionRepository;
import by.bsuir.productservice.repository.SupplierRepository;
import by.bsuir.productservice.repository.SupplyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptSessionService {

    private final ReceiptSessionRepository sessionRepository;
    private final ProductOperationService productOperationService;
    private final ProductOperationRepository operationRepository;
    private final ProductReadModelRepository productRepository;
    private final ProductBatchRepository batchRepository;
    private final SupplierRepository supplierRepository;
    private final SupplyRepository supplyRepository;
    private final PlacementService placementService;
    private final DocumentRegistryService documentRegistryService;
    private final by.bsuir.productservice.repository.InventoryRepository inventoryRepository;
    private final by.bsuir.productservice.client.WarehouseClient warehouseClient;

    @Transactional
    public ReceiptSession createSession(CreateReceiptSessionRequest req, UUID organizationId) {
        if (organizationId == null) {
            throw AppException.badRequest("X-Organization-Id обязателен");
        }
        if (req.items() == null || req.items().isEmpty()) {
            throw AppException.badRequest("Список позиций не может быть пустым");
        }

        Supply plannedSupply = null;
        if (req.supplyId() != null) {
            plannedSupply = supplyRepository.findById(req.supplyId())
                    .orElseThrow(() -> AppException.badRequest(
                            "Плановая поставка не найдена: " + req.supplyId()));
            if (plannedSupply.getStatus() != SupplyStatus.PLANNED
                    && plannedSupply.getStatus() != SupplyStatus.IN_PROGRESS) {
                throw AppException.badRequest(
                        "Плановая поставка уже в статусе " + plannedSupply.getStatus()
                                + ", приёмка по ней невозможна");
            }
            int plannedCount = plannedSupply.getTotalItems() != null ? plannedSupply.getTotalItems() : 0;
            int actualCount = req.items().size();
            if (plannedCount > 0 && actualCount != plannedCount) {
                throw AppException.badRequest(String.format(
                        "Несоответствие количества позиций: планировалось %d, передано %d. "
                                + "Доведите список до %d позиций или измените плановую поставку.",
                        plannedCount, actualCount, plannedCount));
            }
        }

        UUID sessionId = UUID.randomUUID();
        ReceiptSession session = ReceiptSession.builder()
                .sessionId(sessionId)
                .organizationId(organizationId)
                .warehouseId(req.warehouseId())
                .supplierId(req.supplierId())
                .supplyId(req.supplyId())
                .status(ReceiptSessionStatus.PAUSED)
                .generalNotes(req.generalNotes())
                .contractNumber(req.contractNumber())
                .contractDate(req.contractDate())
                .responsibleUserId(req.responsibleUserId())
                .commissionMembers(joinMembers(req.commissionMembers()))
                .createdBy(req.userId())
                .createdAt(LocalDateTime.now())
                .build();
        sessionRepository.save(session);

        if (plannedSupply != null && plannedSupply.getStatus() == SupplyStatus.PLANNED) {
            plannedSupply.setStatus(SupplyStatus.IN_PROGRESS);
            supplyRepository.save(plannedSupply);
        }

        List<UUID> operationIds = new ArrayList<>();
        List<PlacedItem> placedItems = new ArrayList<>();
        for (CreateReceiptSessionRequest.ReceiptItem item : req.items()) {
            UUID effectiveBatchId = resolveBatchId(item, req, organizationId);
            UUID effectiveCellId = item.cellId();
            if (effectiveCellId == null && item.palletPlaceId() != null) {
                effectiveCellId = item.palletPlaceId();
            }
            if (effectiveCellId == null) {
                effectiveCellId = placementService.autoSelectCellForReceipt(
                        req.warehouseId(), item.productId(),
                        item.quantity(), item.unitsPerPackage(),
                        item.storageConditions(), item.packagingType(),
                        item.packageLengthCm(), item.packageWidthCm(),
                        item.packageHeightCm(), item.packageWeightKg(),
                        "WORKER");
                if (effectiveCellId == null) {
                    String prodLabel = productRepository.findById(item.productId())
                            .map(p -> "«" + p.getName() + "»"
                                    + (p.getSku() != null ? " (SKU " + p.getSku() + ")" : ""))
                            .orElse(item.productId().toString());
                    throw AppException.conflict(
                            "Не удалось подобрать место для товара " + prodLabel
                                    + ": нет свободных мест с условиями "
                                    + (item.storageConditions() != null
                                            ? item.storageConditions() : "по умолчанию")
                                    + ", подходящих по габаритам/весу. Проверьте стеллажи "
                                    + (item.packagingType()
                                            == by.bsuir.productservice.model.enums.PackagingType.PALLET
                                            ? "паллетного" : "ячеистого/полочного") + " типа.");
                }
            } else {
                placementService.validateReceiptCellFit(
                        req.warehouseId(), effectiveCellId,
                        item.packageLengthCm(), item.packageWidthCm(),
                        item.packageHeightCm(), item.packageWeightKg(),
                        item.unitsPerPackage(), item.quantity(),
                        item.packagingType(), "WORKER");
            }
            ReceiveProductRequest rpr = new ReceiveProductRequest(
                    item.productId(),
                    effectiveBatchId,
                    req.warehouseId(),
                    effectiveCellId,
                    item.quantity(),
                    req.userId(),
                    req.supplyId(),
                    item.notes()
            );
            UUID opId = productOperationService.receiveItemInSession(rpr, organizationId, sessionId);
            operationIds.add(opId);
            placedItems.add(new PlacedItem(item, effectiveBatchId, effectiveCellId));
        }

        GeneratedDocument receiptOrder = safeRegister(
                "receipt-order",
                buildReceiptOrderPayload(session, req),
                organizationId,
                req.userId());
        if (receiptOrder != null) {
            session.setReceiptOrderDocId(receiptOrder.getId());
        }

        GeneratedDocument receiptAct = safeRegister(
                "receipt-act",
                buildReceiptActPayload(session, req, List.of()),
                organizationId,
                req.userId());
        if (receiptAct != null) {
            session.setReceiptActDocId(receiptAct.getId());
        }

        GeneratedDocument placementList = safeRegister(
                "placement-list",
                buildPlacementListPayload(session, placedItems),
                organizationId,
                req.userId());
        if (placementList != null) {
            session.setPlacementListDocId(placementList.getId());
        }

        List<String> failedDocs = new ArrayList<>();
        if (receiptOrder == null) failedDocs.add("приходный ордер");
        if (receiptAct == null) failedDocs.add("акт приёмки");
        if (placementList == null) failedDocs.add("лист размещения");
        session.setDocumentError(failedDocs.isEmpty() ? null
                : "Не удалось сгенерировать: " + String.join(", ", failedDocs)
                        + ". Документы можно перевыпустить позже.");

        sessionRepository.save(session);
        log.info("Receipt session {} created: {} items, receiptOrder={}, receiptAct={}, placementList={}",
                sessionId, operationIds.size(),
                receiptOrder != null ? receiptOrder.getDocumentNumber() : "—",
                receiptAct != null ? receiptAct.getDocumentNumber() : "—",
                placementList != null ? placementList.getDocumentNumber() : "—");
        return session;
    }

    @Transactional
    public ReceiptSession completeSession(UUID sessionId, UUID userId, UUID organizationId) {
        ReceiptSession session = loadSession(sessionId, organizationId);
        requireStatus(session, ReceiptSessionStatus.PAUSED);

        List<ProductOperation> ops = operationRepository.findBySessionId(sessionId);
        for (ProductOperation op : ops) {
            op.setStatus(OperationStatus.COMPLETED);
        }
        operationRepository.saveAll(ops);

        session.setStatus(ReceiptSessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        sessionRepository.save(session);

        markPlannedSupplyAccepted(session, false);

        log.info("Receipt session {} completed without discrepancies ({} ops, by user {})",
                sessionId, ops.size(), userId);
        return session;
    }

    @Transactional
    public ReceiptSession recordDiscrepancy(
            UUID sessionId, SessionDiscrepancyRequest req, UUID organizationId) {
        ReceiptSession session = loadSession(sessionId, organizationId);
        requireStatus(session, ReceiptSessionStatus.PAUSED);

        List<SessionDiscrepancyRequest.DiscrepancyItem> realDiscrepancies =
                (req.items() == null ? List.<SessionDiscrepancyRequest.DiscrepancyItem>of() : req.items())
                        .stream()
                        .filter(it -> it.actualQty() != null && it.expectedQty() != null
                                && it.actualQty().compareTo(it.expectedQty()) != 0)
                        .toList();
        if (realDiscrepancies.isEmpty()) {
            throw AppException.badRequest(
                    "Расхождений нет: фактические количества совпадают с ожидаемыми. Используйте «Принять без замечаний».");
        }

        if (req.generalNotes() != null) {
            session.setGeneralNotes(req.generalNotes());
        }

        List<ProductOperation> ops = operationRepository.findBySessionId(sessionId);

        Map<UUID, SessionDiscrepancyRequest.DiscrepancyItem> discByProduct = new HashMap<>();
        for (SessionDiscrepancyRequest.DiscrepancyItem d : realDiscrepancies) {
            discByProduct.put(d.productId(), d);
        }

        List<Map<String, Object>> itemRows = buildItemRowsFromOps(ops, discByProduct);
        BigDecimal totalAmount = itemRows.stream()
                .map(m -> (BigDecimal) m.get("amount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map<String, Object>> discrepancyRows = buildDiscrepancyRows(realDiscrepancies);

        applyDiscrepancyToInventory(ops, discByProduct);

        UUID prevActDocId = session.getReceiptActDocId();
        UUID prevOrderDocId = session.getReceiptOrderDocId();

        Map<String, Object> actPayload = baseHeader(session);
        actPayload.put("items", itemRows);
        actPayload.put("totalAmount", totalAmount);
        actPayload.put("discrepancies", discrepancyRows);
        GeneratedDocument receiptAct = safeRegister(
                "receipt-act", actPayload, organizationId, req.userId());
        if (receiptAct != null) {
            session.setReceiptActDocId(receiptAct.getId());
            documentRegistryService.markSuperseded(prevActDocId, receiptAct.getId(), organizationId);
        }

        Map<String, Object> orderPayload = baseHeader(session);
        orderPayload.put("items", itemRows);
        orderPayload.put("totalAmount", totalAmount);
        GeneratedDocument receiptOrder = safeRegister(
                "receipt-order", orderPayload, organizationId, req.userId());
        if (receiptOrder != null) {
            session.setReceiptOrderDocId(receiptOrder.getId());
            documentRegistryService.markSuperseded(prevOrderDocId, receiptOrder.getId(), organizationId);
        }

        for (ProductOperation op : ops) {
            op.setStatus(OperationStatus.COMPLETED);
        }
        operationRepository.saveAll(ops);

        session.setStatus(ReceiptSessionStatus.COMPLETED_WITH_DISCREPANCY);
        session.setCompletedAt(LocalDateTime.now());
        sessionRepository.save(session);

        markPlannedSupplyAccepted(session, true);

        log.info("Receipt session {} completed with {} discrepancies (by user {})",
                sessionId, realDiscrepancies.size(), req.userId());
        return session;
    }

    private void markPlannedSupplyAccepted(ReceiptSession session, boolean withDiscrepancy) {
        if (session.getSupplyId() == null) {
            return;
        }
        SupplyStatus target = withDiscrepancy
                ? SupplyStatus.COMPLETED_WITH_DISCREPANCY : SupplyStatus.ACCEPTED;
        supplyRepository.findById(session.getSupplyId()).ifPresent(supply -> {
            if (supply.getStatus() == target) {
                return;
            }
            supply.setStatus(target);
            supply.setActualDate(LocalDate.now());
            supplyRepository.save(supply);
            log.info("Supply {} → {} after receipt session {}",
                    supply.getSupplyId(), target, session.getSessionId());
        });
    }

    public Page<ReceiptSession> listSessions(
            UUID organizationId, ReceiptSessionStatus status, UUID warehouseId, Pageable pageable) {
        if (organizationId == null) {
            throw AppException.badRequest("X-Organization-Id обязателен");
        }
        ReceiptSessionStatus effectiveStatus = status != null ? status : ReceiptSessionStatus.PAUSED;
        if (warehouseId != null) {
            return sessionRepository.findByOrganizationIdAndWarehouseIdAndStatus(
                    organizationId, warehouseId, effectiveStatus, pageable);
        }
        return sessionRepository.findByOrganizationIdAndStatus(organizationId, effectiveStatus, pageable);
    }

    public ReceiptSession getSession(UUID sessionId, UUID organizationId) {
        return loadSession(sessionId, organizationId);
    }

    public List<ProductOperation> getSessionOperations(UUID sessionId) {
        return operationRepository.findBySessionId(sessionId);
    }

    private ReceiptSession loadSession(UUID sessionId, UUID organizationId) {
        if (organizationId != null) {
            return sessionRepository.findBySessionIdAndOrganizationId(sessionId, organizationId)
                    .orElseThrow(() -> AppException.notFound("Сессия приёмки не найдена: " + sessionId));
        }
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> AppException.notFound("Сессия приёмки не найдена: " + sessionId));
    }

    private void requireStatus(ReceiptSession session, ReceiptSessionStatus expected) {
        if (session.getStatus() != expected) {
            throw AppException.conflict(
                    "Сессия в статусе " + session.getStatus() + ", ожидался " + expected);
        }
    }

    private UUID resolveBatchId(
            CreateReceiptSessionRequest.ReceiptItem item,
            CreateReceiptSessionRequest req,
            UUID organizationId) {
        if (item.batchId() != null) {
            return item.batchId();
        }
        if (item.batchNumber() == null || item.batchNumber().isBlank()) {
            return null;
        }
        Optional<ProductBatch> existing = batchRepository
                .findFirstByOrganizationIdAndBatchNumber(organizationId, item.batchNumber());
        if (existing.isPresent()) {
            log.info("Reusing existing batch {} for number {} (org {})",
                    existing.get().getBatchId(), item.batchNumber(), organizationId);
            return existing.get().getBatchId();
        }
        Supplier supplier = req.supplierId() != null
                ? supplierRepository.findById(req.supplierId()).orElse(null)
                : null;
        ProductBatch batch = ProductBatch.builder()
                .productId(item.productId())
                .organizationId(organizationId)
                .supplyId(req.supplyId())
                .batchNumber(item.batchNumber())
                .expiryDate(item.expiryDate())
                .supplier(supplier != null ? supplier.getName() : null)
                .purchasePrice(item.pricePerUnit())
                .packagingType(item.packagingType())
                .unitsPerPackage(item.unitsPerPackage())
                .packageLengthCm(item.packageLengthCm())
                .packageWidthCm(item.packageWidthCm())
                .packageHeightCm(item.packageHeightCm())
                .packageWeightKg(item.packageWeightKg())
                .storageConditions(item.storageConditions())
                .build();
        batchRepository.save(batch);
        log.info("Auto-created ProductBatch {} for receipt session {} (number={}, expiryDate={}, packaging={}, units/pkg={})",
                batch.getBatchId(), req.warehouseId(), item.batchNumber(), item.expiryDate(),
                item.packagingType(), item.unitsPerPackage());
        return batch.getBatchId();
    }

    private GeneratedDocument safeRegister(
            String type, Map<String, Object> payload, UUID organizationId, UUID userId) {
        try {
            return documentRegistryService.register(null, type, payload, organizationId, userId);
        } catch (Exception e) {
            log.error("Не удалось зарегистрировать документ типа {} (org={}): {}",
                    type, organizationId, e.getMessage(), e);
            return null;
        }
    }

    private List<Map<String, Object>> buildItemRows(CreateReceiptSessionRequest req) {
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int row = 1;
        for (CreateReceiptSessionRequest.ReceiptItem item : req.items()) {
            BigDecimal price = item.pricePerUnit() != null ? item.pricePerUnit() : BigDecimal.ZERO;
            BigDecimal amount = price.multiply(item.quantity());
            total = total.add(amount);
            Map<String, Object> line = new HashMap<>();
            line.put("rowNumber", row++);
            line.put("lineNo", row - 1);
            line.put("quantity", item.quantity());
            line.put("expectedQty", item.quantity());
            line.put("actualQty", item.quantity());
            line.put("differenceQty", BigDecimal.ZERO);
            line.put("price", price);
            line.put("unitPrice", price);
            line.put("amount", amount);
            line.put("totalPrice", amount);
            line.put("batchNumber", item.batchNumber());
            line.put("expiryDate", item.expiryDate());
            productRepository.findById(item.productId()).ifPresent(p -> {
                line.put("productName", p.getName());
                line.put("name", p.getName());
                line.put("sku", p.getSku());
                line.put("productSku", p.getSku());
                line.put("unit", p.getUnitOfMeasure() != null ? p.getUnitOfMeasure() : "шт");
                line.put("unitOfMeasure", p.getUnitOfMeasure() != null ? p.getUnitOfMeasure() : "шт");
            });
            items.add(line);
        }
        return items;
    }

    private Map<String, Object> buildReceiptOrderPayload(
            ReceiptSession session, CreateReceiptSessionRequest req) {
        Map<String, Object> payload = baseHeader(session);
        List<Map<String, Object>> items = buildItemRows(req);
        payload.put("items", items);
        BigDecimal total = items.stream()
                .map(m -> (BigDecimal) m.get("amount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        payload.put("totalAmount", total);
        return payload;
    }

    private Map<String, Object> buildReceiptActPayload(
            ReceiptSession session,
            CreateReceiptSessionRequest req,
            List<SessionDiscrepancyRequest.DiscrepancyItem> discrepancies) {
        Map<String, Object> payload = baseHeader(session);
        List<Map<String, Object>> items = buildItemRows(req);
        payload.put("items", items);
        BigDecimal total = items.stream()
                .map(m -> (BigDecimal) m.get("amount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        payload.put("totalAmount", total);
        payload.put("discrepancies", discrepancies);
        return payload;
    }

    private void applyDiscrepancyToInventory(
            List<ProductOperation> ops,
            Map<UUID, SessionDiscrepancyRequest.DiscrepancyItem> discByProduct) {
        for (ProductOperation op : ops) {
            SessionDiscrepancyRequest.DiscrepancyItem d = discByProduct.get(op.getProductId());
            if (d == null) continue;
            BigDecimal expected = op.getQuantity() != null ? op.getQuantity() : BigDecimal.ZERO;
            BigDecimal actual = d.actualQty() != null ? d.actualQty() : expected;
            BigDecimal delta = actual.subtract(expected);
            if (delta.signum() == 0) continue;

            UUID cellId = op.getToCellId() != null ? op.getToCellId() : op.getFromCellId();
            if (op.getProductId() != null && op.getWarehouseId() != null && cellId != null) {
                inventoryRepository.findExactInventoryForUpdate(
                        op.getProductId(), op.getBatchId(), op.getWarehouseId(), cellId
                ).ifPresent(inv -> {
                    BigDecimal newQty = inv.getQuantity().add(delta);
                    if (newQty.signum() < 0) {
                        throw AppException.badRequest(String.format(
                                "Недостача (%s) превышает фактический остаток в ячейке (%s) "
                                        + "по товару %s — проверьте количество",
                                delta.abs().stripTrailingZeros().toPlainString(),
                                inv.getQuantity().stripTrailingZeros().toPlainString(),
                                op.getProductId()));
                    }
                    inv.setQuantity(newQty);
                    inv.setLastUpdated(LocalDateTime.now());
                    boolean emptyAndUnreserved = newQty.signum() == 0
                            && (inv.getReservedQuantity() == null
                                    || inv.getReservedQuantity().signum() == 0);
                    if (emptyAndUnreserved) {
                        inventoryRepository.delete(inv);
                    } else {
                        inventoryRepository.save(inv);
                    }
                });

                BigDecimal heightDelta = computeReceiveHeightDelta(op.getBatchId(), delta);
                if (heightDelta.signum() != 0) {
                    warehouseClient.adjustSlotHeight(cellId, heightDelta.negate());
                }
            }

            op.setQuantity(actual);
            String suffix = String.format(" [расхождение: план %s, факт %s, тип %s]",
                    expected.stripTrailingZeros().toPlainString(),
                    actual.stripTrailingZeros().toPlainString(),
                    d.discrepancyType() != null ? d.discrepancyType() : "—");
            op.setNotes((op.getNotes() != null ? op.getNotes() : "") + suffix);
        }
        operationRepository.saveAll(ops);
    }

    private BigDecimal computeReceiveHeightDelta(UUID batchId, BigDecimal deltaUnits) {
        if (batchId == null || deltaUnits == null || deltaUnits.signum() == 0) {
            return BigDecimal.ZERO;
        }
        ProductBatch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null || batch.getPackageHeightCm() == null) return BigDecimal.ZERO;
        int upp = (batch.getUnitsPerPackage() != null && batch.getUnitsPerPackage() > 0)
                ? batch.getUnitsPerPackage() : 1;
        BigDecimal absUnits = deltaUnits.abs();
        BigDecimal numPackages = absUnits.divide(
                BigDecimal.valueOf(upp), 0, java.math.RoundingMode.CEILING);
        BigDecimal h = batch.getPackageHeightCm().multiply(numPackages);
        return deltaUnits.signum() > 0 ? h : h.negate();
    }

    private Map<String, Object> baseHeader(ReceiptSession session) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", session.getSessionId().toString());
        payload.put("warehouseId", session.getWarehouseId().toString());
        payload.put("date", LocalDate.now().toString());
        if (session.getCreatedBy() != null) {
            payload.put("userId", session.getCreatedBy().toString());
            payload.put("acceptedBy", session.getCreatedBy().toString());
        }
        UUID chairman = session.getResponsibleUserId() != null
                ? session.getResponsibleUserId() : session.getCreatedBy();
        if (chairman != null) {
            payload.put("chairmanName", chairman.toString());
            payload.put("responsiblePerson", chairman.toString());
            payload.put("responsibleUserId", chairman.toString());
        }
        List<UUID> members = splitMembers(session.getCommissionMembers());
        if (!members.isEmpty()) {
            payload.put("commissionMembers", members);
        }
        if (session.getGeneralNotes() != null) {
            payload.put("generalNotes", session.getGeneralNotes());
        }
        if (session.getContractNumber() != null && !session.getContractNumber().isBlank()) {
            payload.put("contractNumber", session.getContractNumber());
        }
        if (session.getContractDate() != null) {
            payload.put("contractDate", session.getContractDate().toString());
        }
        if (session.getSupplyId() != null) {
            payload.put("supplyId", session.getSupplyId().toString());
        }
        if (session.getSupplierId() != null) {
            Optional<Supplier> supplier = supplierRepository.findById(session.getSupplierId());
            supplier.ifPresent(s -> {
                payload.put("supplierName", s.getName());
                payload.put("supplierInn", s.getUnp());
                payload.put("supplierAddress", s.getAddress());
            });
        }
        return payload;
    }

    private static String joinMembers(List<UUID> members) {
        if (members == null || members.isEmpty()) {
            return null;
        }
        return members.stream().filter(java.util.Objects::nonNull)
                .map(UUID::toString).collect(java.util.stream.Collectors.joining(","));
    }

    private static List<UUID> splitMembers(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        List<UUID> result = new ArrayList<>();
        for (String part : joined.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                try {
                    result.add(UUID.fromString(t));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return result;
    }

    private List<Map<String, Object>> buildItemRowsFromOps(
            List<ProductOperation> ops,
            Map<UUID, SessionDiscrepancyRequest.DiscrepancyItem> discByProduct) {
        List<Map<String, Object>> items = new ArrayList<>();
        int row = 1;
        for (ProductOperation op : ops) {
            ProductBatch batch = op.getBatchId() != null
                    ? batchRepository.findById(op.getBatchId()).orElse(null) : null;
            BigDecimal expected = op.getQuantity() != null ? op.getQuantity() : BigDecimal.ZERO;
            SessionDiscrepancyRequest.DiscrepancyItem d = discByProduct.get(op.getProductId());
            BigDecimal actual = d != null && d.actualQty() != null ? d.actualQty() : expected;
            BigDecimal price = batch != null && batch.getPurchasePrice() != null
                    ? batch.getPurchasePrice() : BigDecimal.ZERO;
            BigDecimal amount = price.multiply(actual);
            Map<String, Object> line = new HashMap<>();
            line.put("rowNumber", row);
            line.put("lineNo", row);
            row++;
            line.put("quantity", actual);
            line.put("expectedQty", expected);
            line.put("actualQty", actual);
            line.put("differenceQty", actual.subtract(expected));
            line.put("price", price);
            line.put("unitPrice", price);
            line.put("amount", amount);
            line.put("totalPrice", amount);
            if (batch != null) {
                line.put("batchNumber", batch.getBatchNumber());
                line.put("expiryDate", batch.getExpiryDate());
            }
            if (d != null) {
                line.put("defectDescription", d.defectDescription());
                line.put("discrepancyType", d.discrepancyType());
            }
            productRepository.findById(op.getProductId()).ifPresent(p -> {
                line.put("productName", p.getName());
                line.put("name", p.getName());
                line.put("sku", p.getSku());
                line.put("productSku", p.getSku());
                line.put("unit", p.getUnitOfMeasure() != null ? p.getUnitOfMeasure() : "шт");
                line.put("unitOfMeasure", p.getUnitOfMeasure() != null ? p.getUnitOfMeasure() : "шт");
            });
            items.add(line);
        }
        return items;
    }

    private List<Map<String, Object>> buildDiscrepancyRows(
            List<SessionDiscrepancyRequest.DiscrepancyItem> discrepancies) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int row = 1;
        for (SessionDiscrepancyRequest.DiscrepancyItem d : discrepancies) {
            Map<String, Object> line = new HashMap<>();
            line.put("rowNumber", row++);
            line.put("expectedQty", d.expectedQty());
            line.put("actualQty", d.actualQty());
            line.put("differenceQty", d.actualQty().subtract(d.expectedQty()));
            line.put("defectDescription", d.defectDescription());
            line.put("discrepancyType", d.discrepancyType());
            productRepository.findById(d.productId()).ifPresent(p -> {
                line.put("productName", p.getName());
                line.put("name", p.getName());
                line.put("sku", p.getSku());
                line.put("productSku", p.getSku());
                line.put("unit", p.getUnitOfMeasure() != null ? p.getUnitOfMeasure() : "шт");
            });
            rows.add(line);
        }
        return rows;
    }

    private record PlacedItem(
            CreateReceiptSessionRequest.ReceiptItem item, UUID batchId, UUID cellId) {
    }

    private Map<String, Object> buildPlacementListPayload(ReceiptSession session, List<PlacedItem> placed) {
        Map<String, Object> payload = baseHeader(session);
        Map<UUID, Map<String, String>> locationCache = new HashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        int row = 1;
        for (PlacedItem pi : placed) {
            CreateReceiptSessionRequest.ReceiptItem it = pi.item();
            Map<String, String> loc = resolveCellLocation(pi.cellId(), locationCache);
            Map<String, Object> line = new HashMap<>();
            line.put("rowNumber", row);
            line.put("lineNo", row++);
            line.put("quantity", it.quantity() != null
                    ? it.quantity().stripTrailingZeros().toPlainString() : "0");
            line.put("batchNumber", it.batchNumber());
            line.put("rackName", loc.get("rackName"));
            line.put("cellCode", loc.get("cellCode"));
            line.put("cellId", pi.cellId() != null ? pi.cellId().toString() : null);
            line.put("storageConditions", it.storageConditions() != null
                    ? it.storageConditions().name() : null);
            productRepository.findById(it.productId()).ifPresent(p -> {
                line.put("productName", p.getName());
                line.put("name", p.getName());
                line.put("sku", p.getSku());
                line.put("unit", p.getUnitOfMeasure() != null ? p.getUnitOfMeasure() : "шт");
            });
            items.add(line);
        }
        payload.put("items", items);
        return payload;
    }

    private Map<String, String> resolveCellLocation(UUID cellId, Map<UUID, Map<String, String>> cache) {
        Map<String, String> empty = Map.of("rackName", "—", "cellCode", "—");
        if (cellId == null) {
            return empty;
        }
        if (cache.containsKey(cellId)) {
            return cache.get(cellId);
        }
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
                    if (rack != null && rack.name() != null) {
                        rackName = rack.name();
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("resolveCellLocation: failed for cell {}: {}", cellId, ex.getMessage());
        }
        Map<String, String> result = Map.of("rackName", rackName, "cellCode", cellCode);
        cache.put(cellId, result);
        return result;
    }
}
