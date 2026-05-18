package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.request.ErpConnectionRequest;
import by.bsuir.productservice.dto.response.ErpConnectionResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.config.SecurityUtils;
import by.bsuir.productservice.service.ErpConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/erp-connections")
@RequiredArgsConstructor
@Tag(name = "ERP-подключения", description = "Управление подключениями к 1С / mock-ERP с шифрованным паролем")
public class ErpConnectionController {

    private final ErpConnectionService service;

    @Operation(summary = "Создать ERP-подключение")
    @PostMapping
    public ResponseEntity<ErpConnectionResponse> create(
            @Valid @RequestBody ErpConnectionRequest request,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        requireDirectorOrAccountant(userRole);
        if (userId == null) {
            throw AppException.badRequest("X-User-Id обязателен");
        }
        ErpConnectionResponse response = service.create(request, organizationId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Список ERP-подключений организации")
    @GetMapping
    public ResponseEntity<List<ErpConnectionResponse>> list(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        requireDirectorOrAccountant(userRole);
        return ResponseEntity.ok(service.list(organizationId));
    }

    @Operation(summary = "Получить ERP-подключение по id")
    @GetMapping("/{connectionId}")
    public ResponseEntity<ErpConnectionResponse> get(
            @PathVariable UUID connectionId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        requireDirectorOrAccountant(userRole);
        return ResponseEntity.ok(service.get(connectionId, organizationId));
    }

    @Operation(summary = "Обновить ERP-подключение")
    @PutMapping("/{connectionId}")
    public ResponseEntity<ErpConnectionResponse> update(
            @PathVariable UUID connectionId,
            @Valid @RequestBody ErpConnectionRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        requireDirectorOrAccountant(userRole);
        return ResponseEntity.ok(service.update(connectionId, request, organizationId));
    }

    @Operation(summary = "Удалить ERP-подключение")
    @DeleteMapping("/{connectionId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID connectionId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        requireDirectorOrAccountant(userRole);
        service.delete(connectionId, organizationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Сделать подключение основным для этого агрегатора")
    @PatchMapping("/{connectionId}/default")
    public ResponseEntity<ErpConnectionResponse> setDefault(
            @PathVariable UUID connectionId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        requireDirectorOrAccountant(userRole);
        return ResponseEntity.ok(service.setDefault(connectionId, organizationId));
    }

    private void requireDirectorOrAccountant(String userRole) {
        String role = SecurityUtils.resolveRole(userRole);
        if (role == null || !(role.equals("DIRECTOR") || role.equals("ACCOUNTANT"))) {
            throw AppException.forbidden("Доступ только для DIRECTOR/ACCOUNTANT");
        }
    }
}
