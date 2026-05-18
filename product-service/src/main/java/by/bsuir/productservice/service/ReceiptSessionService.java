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
import by.bsuir.productservice.model.enums.OperationStatus;
import by.bsuir.productservice.model.enums.ReceiptSessionStatus;
import by.bsuir.productservice.repository.ProductBatchRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import by.bsuir.productservice.repository.ReceiptSessionRepository;
import by.bsuir.productservice.repository.SupplierRepository;
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
    private final DocumentRegistryService documentRegistryService;

    @Transactional
    public ReceiptSession createSession(CreateReceiptSessionRequest req, UUID organizationId) {
        if (organizationId == null) {
            throw AppException.badRequest("X-Organization-Id обязателен");
        }
        if (req.items() == null || req.items().isEmpty()) {
            throw AppException.badRequest("Список позиций не может быть пустым");
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
                .createdBy(req.userId())
                .createdAt(LocalDateTime.now())
                .build();
        sessionRepository.save(session);

        List<UUID> operationIds = new ArrayList<>();
        for (CreateReceiptSessionRequest.ReceiptItem item : req.items()) {
            UUID effectiveBatchId = resolveBatchId(item, req, organizationId);
            ReceiveProductRequest rpr = new ReceiveProductRequest(
                    item.productId(),
                    effectiveBatchId,
                    req.warehouseId(),
                    item.cellId(),
                    item.quantity(),
                    req.userId(),
                    req.supplyId(),
                    item.notes()
            );
            UUID opId = productOperationService.receiveItemInSession(rpr, organizationId, sessionId);
            operationIds.add(opId);
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

        sessionRepository.save(session);
        log.info("Receipt session {} created: {} items, receiptOrder={}, receiptAct={}",
                sessionId, operationIds.size(),
                receiptOrder != null ? receiptOrder.getDocumentNumber() : "—",
                receiptAct != null ? receiptAct.getDocumentNumber() : "—");
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

        Map<String, Object> payload = buildDiscrepancyActPayload(session, req, realDiscrepancies);
        GeneratedDocument discrepancyAct = safeRegister(
                "receipt-act", payload, organizationId, req.userId());
        if (discrepancyAct != null) {
            session.setReceiptActDocId(discrepancyAct.getId());
        }
        if (req.generalNotes() != null) {
            session.setGeneralNotes(req.generalNotes());
        }

        List<ProductOperation> ops = operationRepository.findBySessionId(sessionId);
        for (ProductOperation op : ops) {
            op.setStatus(OperationStatus.COMPLETED);
        }
        operationRepository.saveAll(ops);

        session.setStatus(ReceiptSessionStatus.COMPLETED_WITH_DISCREPANCY);
        session.setCompletedAt(LocalDateTime.now());
        sessionRepository.save(session);

        log.info("Receipt session {} completed with {} discrepancies (by user {})",
                sessionId, realDiscrepancies.size(), req.userId());
        return session;
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
                .build();
        batchRepository.save(batch);
        log.info("Auto-created ProductBatch {} for receipt session {} (number={}, expiryDate={})",
                batch.getBatchId(), req.warehouseId(), item.batchNumber(), item.expiryDate());
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

    private Map<String, Object> buildReceiptOrderPayload(
            ReceiptSession session, CreateReceiptSessionRequest req) {
        Map<String, Object> payload = baseHeader(session, req);
        List<Map<String, Object>> items = new ArrayList<>();
        int row = 1;
        for (CreateReceiptSessionRequest.ReceiptItem item : req.items()) {
            Map<String, Object> line = new HashMap<>();
            line.put("rowNumber", row++);
            line.put("quantity", item.quantity());
            line.put("price", item.pricePerUnit() != null ? item.pricePerUnit() : BigDecimal.ZERO);
            BigDecimal price = item.pricePerUnit() != null ? item.pricePerUnit() : BigDecimal.ZERO;
            line.put("amount", price.multiply(item.quantity()));
            line.put("batchNumber", item.batchNumber());
            line.put("expiryDate", item.expiryDate());
            productRepository.findById(item.productId()).ifPresent(p -> {
                line.put("productName", p.getName());
                line.put("sku", p.getSku());
                line.put("unit", p.getUnitOfMeasure() != null ? p.getUnitOfMeasure() : "шт");
            });
            items.add(line);
        }
        payload.put("items", items);
        return payload;
    }

    private Map<String, Object> buildReceiptActPayload(
            ReceiptSession session,
            CreateReceiptSessionRequest req,
            List<SessionDiscrepancyRequest.DiscrepancyItem> discrepancies) {
        Map<String, Object> payload = baseHeader(session, req);
        payload.put("discrepancies", discrepancies);
        return payload;
    }

    private Map<String, Object> buildDiscrepancyActPayload(
            ReceiptSession session,
            SessionDiscrepancyRequest req,
            List<SessionDiscrepancyRequest.DiscrepancyItem> discrepancies) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", session.getSessionId().toString());
        payload.put("warehouseId", session.getWarehouseId().toString());
        payload.put("date", LocalDate.now().toString());
        payload.put("userId", req.userId().toString());
        if (req.generalNotes() != null) {
            payload.put("generalNotes", req.generalNotes());
        }
        if (session.getSupplierId() != null) {
            Optional<Supplier> supplier = supplierRepository.findById(session.getSupplierId());
            supplier.ifPresent(s -> {
                payload.put("supplierName", s.getName());
                payload.put("supplierInn", s.getUnp());
                payload.put("supplierAddress", s.getAddress());
            });
        }
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
                line.put("sku", p.getSku());
                line.put("unit", p.getUnitOfMeasure() != null ? p.getUnitOfMeasure() : "шт");
            });
            rows.add(line);
        }
        payload.put("discrepancies", rows);
        return payload;
    }

    private Map<String, Object> baseHeader(ReceiptSession session, CreateReceiptSessionRequest req) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", session.getSessionId().toString());
        payload.put("warehouseId", session.getWarehouseId().toString());
        payload.put("date", LocalDate.now().toString());
        payload.put("userId", req.userId().toString());
        if (req.generalNotes() != null) {
            payload.put("generalNotes", req.generalNotes());
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

    private void enrichProduct(ProductReadModel product, Map<String, Object> line) {
        line.put("productName", product.getName());
        line.put("sku", product.getSku());
        line.put("unit", product.getUnitOfMeasure() != null ? product.getUnitOfMeasure() : "шт");
    }
}
