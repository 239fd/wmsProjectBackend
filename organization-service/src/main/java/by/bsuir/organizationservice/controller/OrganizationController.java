package by.bsuir.organizationservice.controller;

import by.bsuir.organizationservice.dto.request.CreateOrganizationRequest;
import by.bsuir.organizationservice.dto.request.UpdateOrganizationRequest;
import by.bsuir.organizationservice.dto.response.InvitationCodeResponse;
import by.bsuir.organizationservice.dto.response.OrganizationDumpResponse;
import by.bsuir.organizationservice.dto.response.OrganizationResponse;
import by.bsuir.organizationservice.model.enums.OrganizationStatus;
import by.bsuir.organizationservice.service.OrganizationService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
@Tag(name = "Организации", description = "API для управления организациями в системе WMS")
public class OrganizationController {

    private final OrganizationService organizationService;

    @Operation(
            summary = "Создать организацию",
            description = "Создает новую организацию в системе. Доступно только для пользователей с ролью DIRECTOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Организация успешно создана",
                    content = @Content(schema = @Schema(implementation = OrganizationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректные данные запроса"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    @PostMapping
    public ResponseEntity<OrganizationResponse> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request,
            @Parameter(description = "ID пользователя", required = true) @RequestHeader("X-User-Id") UUID userId,
            @Parameter(description = "Роль пользователя", required = true) @RequestHeader("X-User-Role") String userRole) {

        if (!"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        OrganizationResponse response = organizationService.createOrganization(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Получить организацию по ID",
            description = "Возвращает информацию об организации по ее уникальному идентификатору"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Организация найдена",
                    content = @Content(schema = @Schema(implementation = OrganizationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Организация не найдена")
    })
    @GetMapping("/{orgId}")
    public ResponseEntity<OrganizationResponse> getOrganization(
            @Parameter(description = "ID организации", required = true) @PathVariable UUID orgId) {
        OrganizationResponse response = organizationService.getOrganization(orgId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Получить список всех организаций",
            description = "Возвращает список всех организаций, с возможностью фильтрации по статусу"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список организаций получен")
    })
    @GetMapping
    public ResponseEntity<List<OrganizationResponse>> getAllOrganizations(
            @Parameter(description = "Фильтр по статусу организации") @RequestParam(required = false) OrganizationStatus status) {

        List<OrganizationResponse> response = status != null
                ? organizationService.getOrganizationsByStatus(status)
                : organizationService.getAllOrganizations();

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Обновить организацию",
            description = "Обновляет информацию об организации. Доступно для DIRECTOR и ACCOUNTANT"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Организация обновлена",
                    content = @Content(schema = @Schema(implementation = OrganizationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Организация не найдена")
    })
    @PutMapping("/{orgId}")
    public ResponseEntity<OrganizationResponse> updateOrganization(
            @Parameter(description = "ID организации", required = true) @PathVariable UUID orgId,
            @Valid @RequestBody UpdateOrganizationRequest request,
            @Parameter(description = "Роль пользователя", required = true) @RequestHeader("X-User-Role") String userRole) {

        if (!"DIRECTOR".equals(userRole) && !"ACCOUNTANT".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        OrganizationResponse response = organizationService.updateOrganization(orgId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Удалить организацию",
            description = "Удаляет организацию и все связанные данные. Доступно только для DIRECTOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Организация удалена",
                    content = @Content(schema = @Schema(implementation = OrganizationDumpResponse.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Организация не найдена")
    })
    @DeleteMapping("/{orgId}")
    public ResponseEntity<OrganizationDumpResponse> deleteOrganization(
            @Parameter(description = "ID организации", required = true) @PathVariable UUID orgId,
            @Parameter(description = "ID пользователя", required = true) @RequestHeader("X-User-Id") UUID userId,
            @Parameter(description = "Роль пользователя", required = true) @RequestHeader("X-User-Role") String userRole) {

        if (!"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        OrganizationDumpResponse response = organizationService.deleteOrganization(orgId, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Сгенерировать инвитационные коды",
            description = "Генерирует инвитационные коды для всех складов организации. Доступно только для DIRECTOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Коды успешно сгенерированы"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Организация не найдена")
    })
    @PostMapping("/{orgId}/invitation-codes/generate")
    public ResponseEntity<List<InvitationCodeResponse>> generateInvitationCodes(
            @Parameter(description = "ID организации", required = true) @PathVariable UUID orgId,
            @Parameter(description = "Роль пользователя", required = true) @RequestHeader("X-User-Role") String userRole) {

        if (!"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<InvitationCodeResponse> response = organizationService.generateInvitationCodes(orgId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Перегенерировать инвитационный код для склада",
            description = "Создает новый инвитационный код для конкретного склада организации. Доступно только для DIRECTOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Код успешно перегенерирован",
                    content = @Content(schema = @Schema(implementation = InvitationCodeResponse.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Организация или склад не найдены")
    })
    @PostMapping("/{orgId}/warehouses/{warehouseId}/invitation-code/regenerate")
    public ResponseEntity<InvitationCodeResponse> regenerateInvitationCode(
            @Parameter(description = "ID организации", required = true) @PathVariable UUID orgId,
            @Parameter(description = "ID склада", required = true) @PathVariable UUID warehouseId,
            @Parameter(description = "Роль пользователя", required = true) @RequestHeader("X-User-Role") String userRole) {

        if (!"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        InvitationCodeResponse response = organizationService.regenerateInvitationCodeForWarehouse(orgId, warehouseId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Получить активные инвитационные коды",
            description = "Возвращает список всех активных инвитационных кодов для организации. Доступно только для DIRECTOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список кодов получен"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Организация не найдена")
    })
    @GetMapping("/{orgId}/invitation-codes")
    public ResponseEntity<List<InvitationCodeResponse>> getActiveInvitationCodes(
            @Parameter(description = "ID организации", required = true) @PathVariable UUID orgId,
            @Parameter(description = "Роль пользователя", required = true) @RequestHeader("X-User-Role") String userRole) {

        if (!"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<InvitationCodeResponse> response = organizationService.getActiveInvitationCodes(orgId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Проверить инвитационный код",
            description = "Проверяет валидность инвитационного кода и возвращает информацию о связанной организации и складе"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Код проверен"),
            @ApiResponse(responseCode = "404", description = "Код не найден или недействителен")
    })
    @GetMapping("/invitation-codes/{code}/validate")
    public ResponseEntity<Map<String, Object>> validateInvitationCode(
            @Parameter(description = "Инвитационный код", required = true) @PathVariable String code) {
        Map<String, Object> response = organizationService.validateInvitationCode(code);
        return ResponseEntity.ok(response);
    }
}
