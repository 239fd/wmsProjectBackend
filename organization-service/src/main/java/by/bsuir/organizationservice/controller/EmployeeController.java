package by.bsuir.organizationservice.controller;

import by.bsuir.organizationservice.dto.AddEmployeeRequest;
import by.bsuir.organizationservice.dto.EmployeeResponse;
import by.bsuir.organizationservice.service.EmployeeManagementService;
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
@RequestMapping("/api/organizations/{orgId}/employees")
@RequiredArgsConstructor
@Tag(name = "Сотрудники", description = "API для управления сотрудниками организации")
public class EmployeeController {

    private final EmployeeManagementService employeeManagementService;

    @Operation(
            summary = "Добавить сотрудника в организацию",
            description = "Добавляет нового сотрудника в организацию. Доступно только для DIRECTOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Сотрудник успешно добавлен",
                    content = @Content(schema = @Schema(implementation = EmployeeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Организация не найдена")
    })
    @PostMapping
    public ResponseEntity<EmployeeResponse> addEmployee(
            @Parameter(description = "ID организации", required = true) @PathVariable UUID orgId,
            @Valid @RequestBody AddEmployeeRequest request,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || !"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        EmployeeResponse response = employeeManagementService.addEmployee(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Удалить сотрудника из организации",
            description = "Удаляет сотрудника из организации. Доступно только для DIRECTOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Сотрудник успешно удален"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Сотрудник или организация не найдены")
    })
    @DeleteMapping("/{userId}")
    public ResponseEntity<Map<String, String>> removeEmployee(
            @Parameter(description = "ID организации", required = true) @PathVariable UUID orgId,
            @Parameter(description = "ID пользователя (сотрудника)", required = true) @PathVariable UUID userId,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || !"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        employeeManagementService.removeEmployee(orgId, userId);
        return ResponseEntity.ok(Map.of(
                "message", "Сотрудник удален из организации",
                "orgId", orgId.toString(),
                "userId", userId.toString()
        ));
    }

    @Operation(
            summary = "Получить список сотрудников организации",
            description = "Возвращает список всех сотрудников организации"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список сотрудников получен"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Организация не найдена")
    })
    @GetMapping
    public ResponseEntity<List<EmployeeResponse>> getOrganizationEmployees(
            @Parameter(description = "ID организации", required = true) @PathVariable UUID orgId,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<EmployeeResponse> response = employeeManagementService.getOrganizationEmployees(orgId);
        return ResponseEntity.ok(response);
    }
}
