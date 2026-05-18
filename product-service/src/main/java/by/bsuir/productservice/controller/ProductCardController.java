package by.bsuir.productservice.controller;

import by.bsuir.productservice.config.SecurityUtils;
import by.bsuir.productservice.service.ProductJourneyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/product-card")
@RequiredArgsConstructor
@Tag(name = "Карточка товара", description = "Полная карточка товара: партии, остатки по ячейкам, история операций (FR-8)")
public class ProductCardController {

    private final ProductJourneyService productJourneyService;

    @Operation(summary = "Карточка товара (JSON)",
            description = "Возвращает данные товара, его партии, текущие остатки по ячейкам и историю операций")
    @GetMapping("/{productId}")
    public ResponseEntity<Map<String, Object>> getProductCard(
            @PathVariable UUID productId,
            @Parameter(description = "Опциональный фильтр по партии") @RequestParam(required = false) UUID batchId,
            @Parameter(description = "Опциональный фильтр по конкретному запасу") @RequestParam(required = false) UUID inventoryId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        userRole = SecurityUtils.resolveRole(userRole);
        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(productJourneyService.getJourney(productId, batchId, inventoryId, organizationId));
    }

    @Operation(summary = "Карточка товара (PDF)",
            description = "Возвращает PDF-отчёт по жизненному циклу товара")
    @GetMapping(value = "/{productId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getProductCardPdf(
            @PathVariable UUID productId,
            @RequestParam(required = false) UUID batchId,
            @RequestParam(required = false) UUID inventoryId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        userRole = SecurityUtils.resolveRole(userRole);
        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        byte[] pdf = productJourneyService.generateJourneyPdf(productId, batchId, inventoryId, organizationId);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=product-card.pdf")
                .body(pdf);
    }
}
