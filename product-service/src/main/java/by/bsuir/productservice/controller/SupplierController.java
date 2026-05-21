package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.request.CreateSupplierRequest;
import by.bsuir.productservice.dto.response.SupplierResponse;
import by.bsuir.productservice.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
@Tag(name = "Поставщики", description = "Справочник контрагентов-поставщиков")
public class SupplierController {

    private final SupplierService supplierService;

    private static final int MAX_PAGE_SIZE = 100;

    @Operation(summary = "Получить список активных поставщиков (пагинация)")
    @GetMapping
    public ResponseEntity<Page<SupplierResponse>> getAll(
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        Pageable capped = capSize(pageable);
        return ResponseEntity.ok(supplierService.getAll(organizationId, capped));
    }

    private static Pageable capSize(Pageable pageable) {
        if (pageable.getPageSize() <= MAX_PAGE_SIZE) return pageable;
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }

    @Operation(summary = "Получить поставщика по ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Поставщик найден"),
            @ApiResponse(responseCode = "404", description = "Поставщик не найден")
    })
    @GetMapping("/{supplierId}")
    public ResponseEntity<SupplierResponse> getById(
            @PathVariable UUID supplierId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return ResponseEntity.ok(supplierService.getById(supplierId, organizationId));
    }

    @Operation(summary = "Создать нового поставщика")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Поставщик создан"),
            @ApiResponse(responseCode = "409", description = "Поставщик с таким ИНН уже существует")
    })
    @PostMapping
    public ResponseEntity<SupplierResponse> create(
            @Valid @RequestBody CreateSupplierRequest request,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supplierService.create(request, organizationId));
    }

    @Operation(summary = "Обновить данные поставщика")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Поставщик обновлён"),
            @ApiResponse(responseCode = "404", description = "Поставщик не найден")
    })
    @PutMapping("/{supplierId}")
    public ResponseEntity<SupplierResponse> update(
            @PathVariable UUID supplierId,
            @Valid @RequestBody CreateSupplierRequest request,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return ResponseEntity.ok(supplierService.update(supplierId, request, organizationId));
    }

    @Operation(summary = "Деактивировать поставщика")
    @ApiResponse(responseCode = "204", description = "Поставщик деактивирован")
    @DeleteMapping("/{supplierId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID supplierId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        supplierService.delete(supplierId, organizationId);
        return ResponseEntity.noContent().build();
    }
}