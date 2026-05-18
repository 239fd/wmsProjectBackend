package by.bsuir.productservice.controller;

import by.bsuir.productservice.config.SecurityUtils;
import by.bsuir.productservice.dto.request.CreateProductRequest;
import by.bsuir.productservice.dto.request.UpdateProductRequest;
import by.bsuir.productservice.dto.response.ProductResponse;
import by.bsuir.productservice.service.ProductJourneyService;
import by.bsuir.productservice.service.ProductService;
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

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Товары", description = "API для управления товарами в системе WMS")
public class ProductController {

    private final ProductService productService;
    private final ProductJourneyService journeyService;

    private static final int MAX_PAGE_SIZE = 100;

    @Operation(summary = "Создать товар", description = "Создает новый товар в системе")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Товар успешно создан",
                    content = @Content(schema = @Schema(implementation = ProductResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        userRole = SecurityUtils.resolveRole(userRole);
        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ProductResponse response = productService.createProduct(request, organizationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Поиск товара по тексту",
            description = "Ищет товары по подстроке в name/SKU/barcode (case-insensitive). Лимит — пагинация (default 20).")
    @ApiResponse(responseCode = "200", description = "Список товаров")
    @GetMapping("/search")
    public ResponseEntity<java.util.List<ProductResponse>> searchProducts(
            @RequestParam("query") String query,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(productService.searchProducts(query, capSize(pageable)));
    }

    @Operation(summary = "Получить товар по ID", description = "Возвращает информацию о товаре по его уникальному идентификатору")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Товар найден"),
            @ApiResponse(responseCode = "404", description = "Товар не найден")
    })
    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProduct(
            @Parameter(description = "ID товара", required = true) @PathVariable UUID productId) {
        ProductResponse response = productService.getProduct(productId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Получить все товары (пагинация)", description = "Возвращает страницу товаров")
    @ApiResponse(responseCode = "200", description = "Список товаров получен")
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllProducts(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(productService.getAllProducts(capSize(pageable)));
    }

    private static Pageable capSize(Pageable pageable) {
        if (pageable.getPageSize() <= MAX_PAGE_SIZE) return pageable;
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }

    @Operation(summary = "Получить товар по SKU", description = "Возвращает товар по его артикулу (SKU)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Товар найден"),
            @ApiResponse(responseCode = "404", description = "Товар не найден")
    })
    @GetMapping("/sku/{sku}")
    public ResponseEntity<ProductResponse> getProductBySku(
            @Parameter(description = "SKU товара", required = true) @PathVariable String sku) {
        ProductResponse response = productService.getProductBySku(sku);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Получить товар по штрих-коду", description = "Возвращает товар по его штрих-коду")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Товар найден"),
            @ApiResponse(responseCode = "404", description = "Товар не найден")
    })
    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<ProductResponse> getProductByBarcode(
            @Parameter(description = "Штрих-код товара", required = true) @PathVariable String barcode) {
        ProductResponse response = productService.getProductByBarcode(barcode);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Получить товары по категории (пагинация)", description = "Возвращает страницу товаров категории")
    @ApiResponse(responseCode = "200", description = "Список товаров получен")
    @GetMapping("/category/{category}")
    public ResponseEntity<Page<ProductResponse>> getProductsByCategory(
            @Parameter(description = "Название категории", required = true) @PathVariable String category,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(productService.getProductsByCategory(category, capSize(pageable)));
    }

    @Operation(summary = "Обновить товар", description = "Обновляет информацию о товаре")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Товар обновлен"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Товар не найден")
    })
    @PutMapping("/{productId}")
    public ResponseEntity<ProductResponse> updateProduct(
            @Parameter(description = "ID товара", required = true) @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        userRole = SecurityUtils.resolveRole(userRole);
        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ProductResponse response = productService.updateProduct(productId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Удалить товар", description = "Удаляет товар из системы. Доступно только для DIRECTOR")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Товар удален"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Товар не найден")
    })
    @DeleteMapping("/{productId}")
    public ResponseEntity<Map<String, String>> deleteProduct(
            @Parameter(description = "ID товара", required = true) @PathVariable UUID productId,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        userRole = SecurityUtils.resolveRole(userRole);
        if (!"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        productService.deleteProduct(productId);
        return ResponseEntity.ok(Map.of("message", "Товар успешно удалён"));
    }

    @Operation(summary = "Карточка маршрута товара (journey)",
            description = "Возвращает историю операций по товару. Поддерживает 3 уровня: productId, batchId, inventoryId")
    @GetMapping("/{productId}/journey")
    public ResponseEntity<Map<String, Object>> getJourney(
            @PathVariable UUID productId,
            @RequestParam(required = false) UUID batchId,
            @RequestParam(required = false) UUID inventoryId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return ResponseEntity.ok(journeyService.getJourney(productId, batchId, inventoryId, organizationId));
    }

    @Operation(summary = "Карточка товара в PDF")
    @GetMapping(value = "/{productId}/journey/pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> getJourneyPdf(
            @PathVariable UUID productId,
            @RequestParam(required = false) UUID batchId,
            @RequestParam(required = false) UUID inventoryId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        byte[] pdf = journeyService.generateJourneyPdf(productId, batchId, inventoryId, organizationId);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=product-journey.pdf")
                .body(pdf);
    }
}
