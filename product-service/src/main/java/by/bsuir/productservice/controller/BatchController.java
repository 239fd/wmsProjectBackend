package by.bsuir.productservice.controller;

import by.bsuir.productservice.config.SecurityUtils;
import by.bsuir.productservice.dto.request.CreateBatchRequest;
import by.bsuir.productservice.dto.response.BatchResponse;
import by.bsuir.productservice.service.BatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Партии товаров", description = "API для управления партиями товаров: создание, получение информации о партиях")
public class BatchController {

    private final BatchService batchService;

    private static final int MAX_PAGE_SIZE = 100;

    @Operation(
            summary = "Создать партию товара",
            description = "Создает новую партию для товара с указанием даты производства, срока годности и поставщика"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Партия успешно создана",
                    content = @Content(schema = @Schema(implementation = BatchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Товар не найден")
    })
    @PostMapping("/products/{productId}/batches")
    public ResponseEntity<BatchResponse> createBatch(
            @Parameter(description = "ID товара", required = true) @PathVariable UUID productId,
            @Valid @RequestBody CreateBatchRequest request,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        userRole = SecurityUtils.resolveRole(userRole);
        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        CreateBatchRequest updatedRequest = new CreateBatchRequest(
                productId,
                request.batchNumber(),
                request.manufactureDate(),
                request.expiryDate(),
                request.supplier(),
                request.purchasePrice(),
                request.supplyId(),
                request.storageConditions()
        );

        BatchResponse response = batchService.createBatch(updatedRequest, organizationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Получить партии товара (пагинация)",
            description = "Возвращает страницу партий для указанного товара"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список партий получен"),
            @ApiResponse(responseCode = "404", description = "Товар не найден")
    })
    @GetMapping("/products/{productId}/batches")
    public ResponseEntity<Page<BatchResponse>> getBatchesByProduct(
            @Parameter(description = "ID товара", required = true) @PathVariable UUID productId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(batchService.getBatchesByProduct(productId, organizationId, capSize(pageable)));
    }

    private static Pageable capSize(Pageable pageable) {
        if (pageable.getPageSize() <= MAX_PAGE_SIZE) return pageable;
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }

    @Operation(
            summary = "Получить партию по ID",
            description = "Возвращает информацию о конкретной партии товара"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Партия найдена",
                    content = @Content(schema = @Schema(implementation = BatchResponse.class))),
            @ApiResponse(responseCode = "404", description = "Партия не найдена")
    })
    @GetMapping("/batches/{batchId}")
    public ResponseEntity<BatchResponse> getBatch(
            @Parameter(description = "ID партии", required = true) @PathVariable UUID batchId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        BatchResponse response = batchService.getBatch(batchId, organizationId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Получить все партии (пагинация)",
            description = "Возвращает страницу всех партий товаров в системе"
    )
    @ApiResponse(responseCode = "200", description = "Список партий получен")
    @GetMapping("/batches")
    public ResponseEntity<Page<BatchResponse>> getAllBatches(
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(batchService.getAllBatches(organizationId, capSize(pageable)));
    }
}
