package by.bsuir.productservice.controller;

import by.bsuir.productservice.config.SecurityUtils;
import by.bsuir.productservice.dto.request.CreateReceiptSessionRequest;
import by.bsuir.productservice.dto.request.SessionDiscrepancyRequest;
import by.bsuir.productservice.dto.response.ReceiptSessionResponse;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ReceiptSession;
import by.bsuir.productservice.model.enums.ReceiptSessionStatus;
import by.bsuir.productservice.service.ReceiptSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/receipt-sessions")
@RequiredArgsConstructor
@Tag(name = "Сессии приёмки", description = "Приёмка товаров по партиям — одна сессия = одна поставка = один акт")
public class ReceiptSessionController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReceiptSessionService sessionService;

    @Operation(summary = "Создать сессию приёмки",
            description = "Принять группу позиций в рамках одной поставки. Создаются N операций приёмки в "
                    + "статусе PAUSED + ОДИН receipt-order + ОДИН receipt-act (с пустыми discrepancies).")
    @PostMapping
    public ResponseEntity<ReceiptSessionResponse> createSession(
            @Valid @RequestBody CreateReceiptSessionRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        userRole = SecurityUtils.resolveRole(userRole);
        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ReceiptSession session = sessionService.createSession(request, organizationId);
        List<ProductOperation> ops = sessionService.getSessionOperations(session.getSessionId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ReceiptSessionResponse.from(session, ops));
    }

    @Operation(summary = "Принять без замечаний",
            description = "Перевод сессии PAUSED → COMPLETED. Все привязанные операции тоже COMPLETED.")
    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<ReceiptSessionResponse> completeSession(
            @PathVariable UUID sessionId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        userRole = SecurityUtils.resolveRole(userRole);
        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ReceiptSession session = sessionService.completeSession(sessionId, userId, organizationId);
        List<ProductOperation> ops = sessionService.getSessionOperations(sessionId);
        return ResponseEntity.ok(ReceiptSessionResponse.from(session, ops));
    }

    @Operation(summary = "Зафиксировать расхождение",
            description = "Регенерирует receipt-act с шаблоном «Акт расхождения», все привязанные операции "
                    + "переводятся в COMPLETED, сессия → COMPLETED_WITH_DISCREPANCY.")
    @PostMapping("/{sessionId}/discrepancy")
    public ResponseEntity<ReceiptSessionResponse> recordDiscrepancy(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SessionDiscrepancyRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        userRole = SecurityUtils.resolveRole(userRole);
        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ReceiptSession session = sessionService.recordDiscrepancy(sessionId, request, organizationId);
        List<ProductOperation> ops = sessionService.getSessionOperations(sessionId);
        return ResponseEntity.ok(ReceiptSessionResponse.from(session, ops));
    }

    @Operation(summary = "Список сессий приёмки (paginated)",
            description = "Default status=PAUSED — для секции «На утверждении» на ReceivePage.")
    @GetMapping
    public ResponseEntity<Page<ReceiptSessionResponse>> listSessions(
            @RequestParam(required = false) ReceiptSessionStatus status,
            @RequestParam(required = false) UUID warehouseId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        Page<ReceiptSession> sessions =
                sessionService.listSessions(organizationId, status, warehouseId, capSize(pageable));
        return ResponseEntity.ok(sessions.map(s ->
                ReceiptSessionResponse.from(s, sessionService.getSessionOperations(s.getSessionId()))));
    }

    @Operation(summary = "Детали сессии приёмки")
    @GetMapping("/{sessionId}")
    public ResponseEntity<ReceiptSessionResponse> getSession(
            @PathVariable UUID sessionId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        ReceiptSession session = sessionService.getSession(sessionId, organizationId);
        List<ProductOperation> ops = sessionService.getSessionOperations(sessionId);
        return ResponseEntity.ok(ReceiptSessionResponse.from(session, ops));
    }

    private static Pageable capSize(Pageable pageable) {
        if (pageable.getPageSize() <= MAX_PAGE_SIZE) return pageable;
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }
}
